/*
 * Часть nfcompat. Мини-замена `org.apache.maven.artifact.versioning.DefaultArtifactVersion`
 * (в настоящем Maven это внутренний класс `AbstractArtifactVersion`). Зачем — см.
 * комментарий в ArtifactVersion.java.
 *
 * Разбор и сравнение УПРОЩЕНЫ по сравнению с Maven:
 *  - версия режется по '-': всё до первого дефиса — числа через точки
 *    (major.minor.incremental.buildNumber), всё после — квалификатор;
 *  - `compareTo` сравнивает числовые части, затем квалификаторы: пустой (релиз)
 *    считается БОЛЬШЕ любого непустого, непустые сравниваются как строки.
 *    У Maven есть отдельная таблица известных квалификаторов (alpha < beta < milestone
 *    < rc < snapshot < "" < sp) — здесь её нет.
 *
 * Кому это важно: то, что реально делает аддон (extendedae_plus) — печатает версию
 * через `toString()`. Арифметика `compareTo` в нашем шиме пока никем не используется,
 * поэтому приблизительный порядок приемлем; если понадобится строгий Maven-parity —
 * переписывать здесь, не трогая вызовы.
 */
package org.apache.maven.artifact.versioning;

import org.jspecify.annotations.Nullable;

public final class DefaultArtifactVersion implements ArtifactVersion {

    private String version;
    private int[] parts;
    @Nullable
    private String qualifier;

    public DefaultArtifactVersion(String version) {
        parseVersion(version);
    }

    @Override
    public void parseVersion(String version) {
        this.version = version == null ? "" : version;
        parse(this.version);
    }

    private void parse(String raw) {
        String numeric = raw;
        String qual = null;
        int dash = raw.indexOf('-');
        if (dash >= 0) {
            numeric = raw.substring(0, dash);
            qual = raw.substring(dash + 1);
        }
        var tokens = numeric.isEmpty() ? new String[0] : numeric.split("\\.");
        var parsed = new int[4];
        for (int i = 0; i < parsed.length && i < tokens.length; i++) {
            try {
                parsed[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException ignored) {
                // нечисловой сегмент - как в Maven, считаем 0
                parsed[i] = 0;
            }
        }
        this.parts = parsed;
        this.qualifier = qual == null || qual.isEmpty() ? null : qual;
    }

    @Override
    public String getVersionString() {
        return version;
    }

    @Override
    public int getMajorVersion() {
        return parts[0];
    }

    @Override
    public int getMinorVersion() {
        return parts[1];
    }

    @Override
    public int getIncrementalVersion() {
        return parts[2];
    }

    @Override
    public int getBuildNumber() {
        return parts[3];
    }

    @Override
    @Nullable
    public String getQualifier() {
        return qualifier;
    }

    @Override
    public int compareTo(ArtifactVersion other) {
        if (other == null) {
            return 1; // Maven: null считается младше любой версии
        }
        String otherString = other.getVersionString();
        if (version.equals(otherString)) {
            return 0;
        }
        if (other instanceof DefaultArtifactVersion mine) {
            for (int i = 0; i < parts.length; i++) {
                int cmp = Integer.compare(parts[i], mine.parts[i]);
                if (cmp != 0) {
                    return cmp;
                }
            }
            // Релиз (без квалификатора) больше любой пре-версии.
            var left = qualifier;
            var right = mine.qualifier;
            if (left == null || right == null) {
                return left == null ? (right == null ? 0 : 1) : -1;
            }
            return left.compareTo(right);
        }
        // Чужая реализация - сравниваем строками, чтобы не врать о числах.
        return version.compareTo(otherString);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ArtifactVersion other && version.equals(other.getVersionString());
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    @Override
    public String toString() {
        return version;
    }
}
