import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class AwAudit {

    static final String[] TARGET_PREFIXES = {
            "net/minecraft/", "com/mojang/blaze3d/", "com/mojang/renderpearl/", "com/mojang/math/",
            "com/mojang/serialization/", "com/mojang/datafixers/", "com/mojang/brigadier/", "com/mojang/authlib/"
    };

    static class CI {
        String name, sup;
        int access;
        List<String> itf = new ArrayList<>();
        Map<String, Integer> fields = new HashMap<>();   // name:desc
        Map<String, Integer> methods = new HashMap<>();  // name+desc
        Map<String, Integer> methodByName = new HashMap<>();
        boolean isGuideme;
        String src; // which jar
    }

    static Map<String, CI> ALL = new HashMap<>();

    public static void main(String[] args) throws Exception {
        String gmJar = args[args.length - 2];
        String out = args[args.length - 1];

        for (int i = 0; i < args.length - 2; i++) load(args[i], false);
        load(gmJar, true);
        System.err.println("classes loaded: " + ALL.size());

        // ---- pass 1: guideme -> vanilla refs
        Map<String, Ref> refs = new LinkedHashMap<>();
        List<String> guidemeSubclasses = new ArrayList<>();

        for (CI ci : new ArrayList<>(ALL.values())) {
            if (!ci.isGuideme) continue;
            if (!ci.name.startsWith("guideme/")) continue;
            String gsup = ci.sup;
            if (gsup != null && isVanilla(gsup)) guidemeSubclasses.add(ci.name + " extends " + gsup);
        }

        try (ZipFile zf = new ZipFile(gmJar)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (!ze.getName().endsWith(".class")) continue;
                ClassNode cn = new ClassNode();
                try (InputStream in = zf.getInputStream(ze)) {
                    new ClassReader(in).accept(cn, 0);
                }
                scanClass(cn, refs);
            }
        }

        // ---- output
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"))) {
            pw.println("### RELATIONSHIPS: guideme subclasses of vanilla");
            Collections.sort(guidemeSubclasses);
            for (String s : guidemeSubclasses) pw.println(s);
            pw.println();

            pw.println("### REFS");
            pw.println("verdict\tsection\towner\tname\tdesc\tkind\tmemberAccess\tguidemeClass\tguidemeMethod\tcount\tnote");
            for (Ref r : refs.values()) {
                pw.println(r.verdict + "\t" + r.section + "\t" + r.owner + "\t" + r.name + "\t" + r.desc + "\t" + r.kind + "\t"
                        + r.memberAccess + "\t" + r.users.iterator().next() + "\t" + r.methodsString() + "\t" + r.count + "\t" + r.note);
            }
        }

        // stats
        Map<String, Integer> stats = new TreeMap<>();
        for (Ref r : refs.values()) stats.merge(r.verdict, 1, Integer::sum);
        System.err.println("VERDICTS: " + stats);
        System.err.println("wrote " + out);
    }

    static boolean isVanilla(String n) {
        for (String p : TARGET_PREFIXES) if (n.startsWith(p)) return true;
        return false;
    }

    static void load(String jar, boolean guideme) throws IOException {
        try (ZipFile zf = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (!ze.getName().endsWith(".class")) continue;
                ClassReader cr;
                try (InputStream in = zf.getInputStream(ze)) {
                    cr = new ClassReader(in);
                }
                CI ci = new CI();
                ci.name = cr.getClassName();
                ci.access = cr.getAccess();
                ci.sup = cr.getSuperName();
                ci.isGuideme = guideme;
                ci.src = jar;
                for (String s : cr.getInterfaces()) ci.itf.add(s);
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public FieldVisitor visitField(int access, String name, String desc, String sig, Object val) {
                        ci.fields.put(name + ":" + desc, access);
                        return null;
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                        ci.methods.put(name + desc, access);
                        ci.methodByName.putIfAbsent(name, access);
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                CI prev = ALL.put(ci.name, ci);
                if (prev != null && !guideme && prev.isGuideme) { /* ignore */ }
            }
        }
    }

    static class Ref {
        String owner, name, desc, kind, decl;
        String verdict, memberAccess, section, note = "";
        Set<String> users = new TreeSet<>();
        Map<String, Integer> methods = new LinkedHashMap<>();
        int count;

        String methodsString() {
            return String.join(" ; ", methods.keySet());
        }
    }

    static String key(String kind, String owner, String name, String desc) {
        return kind + "|" + owner + "|" + name + "|" + desc;
    }

    static boolean isGuidemeClass(String n) {
        CI ci = ALL.get(n);
        return ci != null && ci.isGuideme;
    }

    static void scanClass(ClassNode cn, Map<String, Ref> refs) {
        String cls = cn.name;
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            AbstractInsnNode prev = null;
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode f) {
                    boolean ov = isVanilla(f.owner);
                    if (ov || isGuidemeClass(f.owner)) {
                        Ref r = newRef("field", f.owner, f.name, f.desc);
                        addUse(r, cls, mn.name + mn.desc);
                        classifyField(r, cls, f, prev, ov);
                        if (r.verdict != null) refs.putIfAbsent(key("field", r.owner, r.name, r.desc), r);
                    }
                } else if (insn instanceof MethodInsnNode m) {
                    boolean ov = isVanilla(m.owner);
                    if (ov || isGuidemeClass(m.owner)) {
                        Ref r = newRef("method", m.owner, m.name, m.desc);
                        addUse(r, cls, mn.name + mn.desc);
                        classifyMethod(r, cls, m, prev, ov);
                        if (r.verdict != null) refs.putIfAbsent(key("method", r.owner, r.name, r.desc), r);
                    }
                }
                prev = insn;
            }
        }
    }

    static Ref newRef(String kind, String owner, String name, String desc) {
        Ref r = new Ref();
        r.kind = kind;
        r.owner = owner;
        r.name = name;
        r.desc = desc;
        return r;
    }

    static void addUse(Ref r, String cls, String meth) {
        r.users.add(cls);
        r.methods.merge(meth, 1, Integer::sum);
        r.count++;
    }

    static String accName(int a) {
        if ((a & Opcodes.ACC_PUBLIC) != 0) return "public";
        if ((a & Opcodes.ACC_PROTECTED) != 0) return "protected";
        if ((a & Opcodes.ACC_PRIVATE) != 0) return "private";
        return "package";
    }

    // ---------- resolution ----------
    static class Resolved {
        String decl;
        int access;
        boolean found;
        boolean descMismatch;
    }

    static Resolved resolveField(String owner, String name, String desc) {
        Resolved r = new Resolved();
        Deque<String> q = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        q.add(owner);
        String nameOnlyDecl = null;
        int nameOnlyAcc = 0;
        while (!q.isEmpty()) {
            String cur = q.poll();
            if (cur == null || !seen.add(cur)) continue;
            CI ci = ALL.get(cur);
            if (ci == null) continue;
            Integer a = ci.fields.get(name + ":" + desc);
            if (a != null) {
                r.found = true; r.decl = cur; r.access = a; return r;
            }
            for (String k : ci.fields.keySet()) {
                if (k.startsWith(name + ":")) { nameOnlyDecl = cur; nameOnlyAcc = ci.fields.get(k); }
            }
            if (ci.sup != null) q.add(ci.sup);
            q.addAll(ci.itf);
        }
        if (nameOnlyDecl != null) {
            r.found = true; r.decl = nameOnlyDecl; r.access = nameOnlyAcc; r.descMismatch = true;
        }
        return r;
    }

    static Resolved resolveMethod(String owner, String name, String desc) {
        Resolved r = new Resolved();
        Deque<String> q = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        q.add(owner);
        String nameOnlyDecl = null;
        int nameOnlyAcc = 0;
        while (!q.isEmpty()) {
            String cur = q.poll();
            if (cur == null || !seen.add(cur)) continue;
            CI ci = ALL.get(cur);
            if (ci == null) continue;
            Integer a = ci.methods.get(name + desc);
            if (a != null) {
                r.found = true; r.decl = cur; r.access = a; return r;
            }
            for (Map.Entry<String, Integer> e : ci.methods.entrySet()) {
                if (e.getKey().startsWith(name + "(")) { nameOnlyDecl = cur; nameOnlyAcc = e.getValue(); break; }
            }
            if (ci.sup != null) q.add(ci.sup);
            q.addAll(ci.itf);
        }
        if (nameOnlyDecl != null) {
            r.found = true; r.decl = nameOnlyDecl; r.access = nameOnlyAcc; r.descMismatch = true;
        }
        return r;
    }

    static boolean isSubclassOf(String cls, String sup) {
        Deque<String> q = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        q.add(cls);
        while (!q.isEmpty()) {
            String cur = q.poll();
            if (cur == null || !seen.add(cur)) continue;
            if (cur.equals(sup)) return true;
            CI ci = ALL.get(cur);
            if (ci == null) continue;
            if (ci.sup != null) q.add(ci.sup);
            q.addAll(ci.itf);
        }
        return false;
    }

    static String pkg(String n) {
        int i = n.lastIndexOf('/');
        return i < 0 ? "" : n.substring(0, i);
    }

    static String section(String guidemeClass) {
        String c = guidemeClass;
        if (c.startsWith("guideme/internal/siteexport") || c.startsWith("guideme/scene/export")) return "site-export";
        if (c.contains("siteexport")) return "site-export";
        if (c.startsWith("guideme/internal/scene") || c.startsWith("guideme/scene/") ) return "scene";
        if (c.startsWith("guideme/internal/screen")) return "guide-ui";
        if (c.startsWith("guideme/internal/web") || c.startsWith("guideme/web")) return "web";
        if (c.startsWith("guideme/document")) return "document";
        if (c.startsWith("guideme/internal/shaded") || c.startsWith("guideme/libs")) return "vendored-lib";
        if (c.startsWith("guideme/internal/")) return "misc-internal";
        if (c.startsWith("guideme/render")) return "render";
        return "other";
    }

    static void classifyField(Ref r, String cls, FieldInsnNode f, AbstractInsnNode prev, boolean ownerVanilla) {
        r.section = section(cls);
        Resolved res = resolveField(f.owner, f.name, f.desc);
        if (!res.found) {
            if (!ownerVanilla) { r.verdict = null; return; }
            r.verdict = "MISSING";
            r.memberAccess = "-";
            r.note = "member not found in fabric vanilla at all";
            return;
        }
        if (!isVanilla(res.decl)) { r.verdict = null; return; } // internal guideme member
        r.decl = res.decl;
        boolean write = f.getOpcode() == Opcodes.PUTFIELD || f.getOpcode() == Opcodes.PUTSTATIC;
        r.memberAccess = accName(res.access) + (res.descMismatch ? "(desc-mismatch!)" : "") + (write ? "+WRITE" : "");
        String acc = accName(res.access);
        switch (acc) {
            case "public":
                r.verdict = "ok";
                return;
            case "private":
                r.verdict = "ILLEGAL-private";
                r.note = "declared in " + res.decl + (f.owner.equals(res.decl) ? "" : " (via " + f.owner + ")");
                return;
            case "package":
                if (pkg(cls).equals(pkg(res.decl))) { r.verdict = "ok"; }
                else { r.verdict = "ILLEGAL-package"; r.note = "declared in " + res.decl; }
                return;
            case "protected":
                if (cls.equals(res.decl) || isSubclassOf(cls, res.decl)) {
                    boolean thisAccess = prev != null && prev.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) prev).var == 0;
                    if (thisAccess) { r.verdict = "ok"; r.note = "protected, this-access from subclass"; }
                    else { r.verdict = "CHECK-protected-other"; r.note = "protected + non-this receiver, decl=" + res.decl; }
                } else {
                    r.verdict = "ILLEGAL-protected";
                    r.note = "declared in " + res.decl + " (not a subclass)";
                }
                return;
        }
        r.verdict = "UNKNOWN";
    }

    static void classifyMethod(Ref r, String cls, MethodInsnNode m, AbstractInsnNode prev, boolean ownerVanilla) {
        r.section = section(cls);
        Resolved res = resolveMethod(m.owner, m.name, m.desc);
        if (!res.found) {
            if (!ownerVanilla) { r.verdict = null; return; }
            r.verdict = "MISSING";
            r.memberAccess = "-";
            r.note = "member not found in fabric vanilla at all";
            return;
        }
        if (!isVanilla(res.decl)) { r.verdict = null; return; }
        r.decl = res.decl;
        r.memberAccess = accName(res.access) + (res.descMismatch ? "(desc-mismatch!)" : "");
        String acc = accName(res.access);
        switch (acc) {
            case "public":
                r.verdict = "ok";
                return;
            case "private":
                r.verdict = "ILLEGAL-private";
                r.note = "declared in " + res.decl + (m.owner.equals(res.decl) ? "" : " (via " + m.owner + ")");
                return;
            case "package":
                if (pkg(cls).equals(pkg(res.decl))) r.verdict = "ok";
                else { r.verdict = "ILLEGAL-package"; r.note = "declared in " + res.decl; }
                return;
            case "protected":
                if (cls.equals(res.decl) || isSubclassOf(cls, res.decl)) {
                    boolean thisAccess = prev != null && prev.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) prev).var == 0;
                    if (thisAccess) { r.verdict = "ok"; r.note = "protected, this-access from subclass"; }
                    else { r.verdict = "CHECK-protected-other"; r.note = "protected + non-this receiver, decl=" + res.decl; }
                } else {
                    r.verdict = "ILLEGAL-protected";
                    r.note = "declared in " + res.decl + " (not a subclass)";
                }
                return;
        }
        r.verdict = "UNKNOWN";
    }
}
