package com.dimsimd.neocomputers.component.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

public record StorageData(int capacityMb, String type, int throughputMbS, String installedOs, List<String> terminalLog, int bootCount, String diskImage) {
    private static final Codec<StorageData> CURRENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("capacity_mb").forGetter(StorageData::capacityMb),
        Codec.STRING.fieldOf("type").forGetter(StorageData::type),
        Codec.INT.fieldOf("throughput_mb_s").forGetter(StorageData::throughputMbS),
        Codec.STRING.optionalFieldOf("installed_os", "").forGetter(StorageData::installedOs),
        Codec.STRING.listOf().optionalFieldOf("terminal_log", List.of()).forGetter(StorageData::terminalLog),
        Codec.INT.optionalFieldOf("boot_count", 0).forGetter(StorageData::bootCount),
        Codec.STRING.optionalFieldOf("disk_image", "").forGetter(StorageData::diskImage)
    ).apply(instance, StorageData::new));

    private static final Codec<StorageData> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("capacity_gb").forGetter(data -> data.capacityMb() / 1024),
        Codec.STRING.fieldOf("type").forGetter(StorageData::type),
        Codec.INT.fieldOf("throughput_mb_s").forGetter(StorageData::throughputMbS)
    ).apply(instance, (capacityGb, type, throughputMbS) -> new StorageData(capacityGb * 1024, type, throughputMbS)));

    public static final Codec<StorageData> CODEC = Codec.either(CURRENT_CODEC, LEGACY_CODEC)
        .xmap(either -> either.map(data -> data, data -> data), Either::left);

    public StorageData(int capacityMb, String type, int throughputMbS) {
        this(capacityMb, type, throughputMbS, "", List.of(), 0, "");
    }

    public StorageData {
        installedOs = installedOs == null ? "" : installedOs;
        terminalLog = List.copyOf(terminalLog == null ? List.of() : terminalLog);
        diskImage = diskImage == null ? "" : diskImage;
    }

    public boolean hasInstalledOs() {
        return !installedOs.isBlank();
    }

    public StorageData withInstalledOs(String osName) {
        return new StorageData(capacityMb, type, throughputMbS, osName, terminalLog, bootCount, diskImage);
    }

    public StorageData withTerminalLog(List<String> lines) {
        return new StorageData(capacityMb, type, throughputMbS, installedOs, lines, bootCount, diskImage);
    }

    public StorageData withBootCount(int newBootCount) {
        return new StorageData(capacityMb, type, throughputMbS, installedOs, terminalLog, newBootCount, diskImage);
    }

    public StorageData withDiskImage(String newDiskImage) {
        return new StorageData(capacityMb, type, throughputMbS, installedOs, terminalLog, bootCount, newDiskImage);
    }

    public StorageData withVmState(String newInstalledOs, List<String> lines, int newBootCount, String newDiskImage) {
        return new StorageData(capacityMb, type, throughputMbS, newInstalledOs, lines, newBootCount, newDiskImage);
    }
}
