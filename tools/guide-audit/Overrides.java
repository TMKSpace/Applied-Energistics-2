import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Finds guideme methods that "override" vanilla members which are not public/protected in fabric vanilla. */
public class Overrides {

    static class CI {
        String name, sup; int access; List<String> itf = new ArrayList<>();
        Map<String, Integer> methods = new HashMap<>();
        Map<String, Integer> mnames = new HashMap<>();
        Map<String, Integer> fields = new HashMap<>();
        boolean isGuideme;
    }

    static Map<String, CI> ALL = new HashMap<>();

    public static void main(String[] a) throws Exception {
        load(a[0], false);
        load(a[1], true);
        StringBuilder sb = new StringBuilder();
        for (CI ci : ALL.values()) {
            if (!ci.isGuideme || !ci.name.startsWith("guideme/")) continue;
            boolean vanillaAncestor = false;
            for (String s = ci.sup; s != null; ) {
                if (s.startsWith("net/minecraft/") || s.startsWith("com/mojang/")) { vanillaAncestor = true; break; }
                CI p = ALL.get(s);
                s = p == null ? null : p.sup;
            }
            if (!vanillaAncestor) continue;
            for (Map.Entry<String, Integer> e : ci.methods.entrySet()) {
                String sig = e.getKey();
                if (ci.name.replace('$', '/').contains("/")) { /* keep */ }
                String name = sig.substring(0, sig.indexOf('('));
                String desc = sig.substring(sig.indexOf('('));
                if (name.startsWith("<")) continue;
                // find in ancestors
                String found = null; int acc = 0; boolean isStatic = false;
                Deque<String> q = new ArrayDeque<>();
                Set<String> seen = new HashSet<>();
                if (ci.sup != null) q.add(ci.sup);
                q.addAll(ci.itf);
                while (!q.isEmpty()) {
                    String cur = q.poll();
                    if (cur == null || !seen.add(cur)) continue;
                    CI p = ALL.get(cur);
                    if (p == null) continue;
                    Integer x = p.methods.get(name + desc);
                    if (x != null) { found = cur; acc = x; isStatic = (x & Opcodes.ACC_STATIC) != 0; break; }
                    for (Map.Entry<String, Integer> m2 : p.methods.entrySet()) {
                        if (m2.getKey().startsWith(name + "(")) { found = cur; acc = m2.getValue(); break; }
                    }
                    if (p.sup != null) q.add(p.sup);
                    q.addAll(p.itf);
                }
                if (found == null) continue;
                String accS = (acc & Opcodes.ACC_PUBLIC) != 0 ? "public"
                        : (acc & Opcodes.ACC_PROTECTED) != 0 ? "protected"
                        : (acc & Opcodes.ACC_PRIVATE) != 0 ? "private" : "package";
                String verdict = accS.equals("private") || accS.equals("package") ? "DEAD-OVERRIDE" : "ok-override";
                if ((e.getValue() & Opcodes.ACC_STATIC) != 0 || (e.getValue() & Opcodes.ACC_BRIDGE) != 0) verdict += "(bridge/static)";
                sb.append(verdict).append('\t').append(ci.name).append('\t').append(name).append(desc)
                        .append('\t').append(accS).append('\t').append(found).append('\n');
            }
            // fields that shadow/rename
        }
        System.out.print(sb);
    }

    static void load(String jar, boolean g) throws IOException {
        try (ZipFile zf = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (!ze.getName().endsWith(".class")) continue;
                ClassReader cr;
                try (InputStream in = zf.getInputStream(ze)) { cr = new ClassReader(in); }
                CI ci = new CI();
                ci.name = cr.getClassName(); ci.access = cr.getAccess(); ci.sup = cr.getSuperName(); ci.isGuideme = g;
                for (String s : cr.getInterfaces()) ci.itf.add(s);
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    public FieldVisitor visitField(int acc, String n, String d, String sg, Object v) { ci.fields.put(n + ":" + d, acc); return null; }
                    public MethodVisitor visitMethod(int acc, String n, String d, String sg, String[] ex) { ci.methods.put(n + d, acc); ci.mnames.putIfAbsent(n, acc); return null; }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                ALL.put(ci.name, ci);
            }
        }
    }
}
