package com.dimsimd.neocomputers.component.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MotherboardData(String socket, int ramSlots, int pcieSlots, int sataSlots) {
    public static final Codec<MotherboardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("socket").forGetter(MotherboardData::socket),
        Codec.INT.fieldOf("ram_slots").forGetter(MotherboardData::ramSlots),
        Codec.INT.fieldOf("pcie_slots").forGetter(MotherboardData::pcieSlots),
        Codec.INT.fieldOf("sata_slots").forGetter(MotherboardData::sataSlots)
    ).apply(instance, MotherboardData::new));
}
