package com.dimsimd.neocomputers.component.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record FirmwareData(String id, String version) {
    public static final Codec<FirmwareData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(FirmwareData::id),
        Codec.STRING.fieldOf("version").forGetter(FirmwareData::version)
    ).apply(instance, FirmwareData::new));
}
