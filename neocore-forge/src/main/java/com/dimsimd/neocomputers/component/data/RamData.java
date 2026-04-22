package com.dimsimd.neocomputers.component.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record RamData(int memoryMb, int frequencyMhz, String type) {
    public static final Codec<RamData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("memory_mb").forGetter(RamData::memoryMb),
        Codec.INT.fieldOf("frequency_mhz").forGetter(RamData::frequencyMhz),
        Codec.STRING.fieldOf("type").forGetter(RamData::type)
    ).apply(instance, RamData::new));
}
