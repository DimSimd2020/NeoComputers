package com.dimsimd.neocomputers.component.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record CpuData(int cores, float frequencyGhz, String socket) {
    public static final Codec<CpuData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("cores").forGetter(CpuData::cores),
        Codec.FLOAT.fieldOf("frequency_ghz").forGetter(CpuData::frequencyGhz),
        Codec.STRING.fieldOf("socket").forGetter(CpuData::socket)
    ).apply(instance, CpuData::new));
}
