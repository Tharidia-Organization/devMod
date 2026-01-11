package com.devmod.clone.block.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.devmod.clone.data.BioscanData;

/**
 * Shared access interface for Neurocell variants.
 * Keeps Imprinter/Reformer logic agnostic to chamber size.
 */
public interface NeurocellAccess {
    boolean isDataReady();

    boolean hasEmptyBioscanner();

    @Nullable
    BioscanData consumeData();

    void fillBioscannerFromImprinter(@Nonnull BioscanData data);
}
