/*
 * Часть nfcompat. Шим net.neoforged.neoforge.model.data для Fabric.
 * Вендорено из NeoForge 26.3 (LGPL-2.1-only).
 *
 * ModelDataManager намеренно НЕ портирован: AE2 его не использует вообще
 * (0 упоминаний), а он целиком построен на NeoForge-патче
 * BlockGetter#getModelData(BlockPos), которого в ванили нет.
 */

@NullMarked
package net.neoforged.neoforge.model.data;

import org.jspecify.annotations.NullMarked;
