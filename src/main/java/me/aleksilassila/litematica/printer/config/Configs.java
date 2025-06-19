package me.aleksilassila.litematica.printer.config;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigInteger;

public class Configs
{
    public static final ConfigInteger SAVE_INTERVAL = new ConfigInteger("saveInterval", 500, 100, 10000,
            "litematica-printer.config.generic.comment.saveInterval")
            .translatedName("litematica-printer.config.generic.name.saveInterval");

    public static final ConfigInteger SCAN_BLOCKS_PER_TICK = new ConfigInteger("scanBlocksPerTick", 500, 100, 10000,
            "litematica-printer.config.generic.comment.scanBlocksPerTick")
            .translatedName("litematica-printer.config.generic.name.scanBlocksPerTick");

    public static final ConfigInteger PLACE_BLOCKS_PER_TICK = new ConfigInteger("placeBlocksPerTick", 1, 1, 100,
            "litematica-printer.config.generic.comment.placeBlocksPerTick")
            .translatedName("litematica-printer.config.generic.name.placeBlocksPerTick");

    public static final ConfigInteger FILL_HEIGHT = new ConfigInteger("maxFillHeight", 0, -128, 1000,
            "litematica-printer.config.generic.comment.maxFillHeight")
            .translatedName("litematica-printer.config.generic.name.maxFillHeight");

    public static final ConfigBoolean FILL_EMPTY = new ConfigBoolean("fillEmptyBeforePrint", false,
            "litematica-printer.config.generic.comment.fillEmptyBeforePrint",
            "litematica-printer.config.generic.prettyName.fillEmptyBeforePrint")
            .translatedName("litematica-printer.config.generic.name.fillEmptyBeforePrint");

    public static final ConfigBoolean PRINT_MODE = new ConfigBoolean("printingMode", false,
            "litematica-printer.config.generic.comment.printingMode",
            "litematica-printer.config.generic.prettyName.printingMode")
            .translatedName("litematica-printer.config.generic.name.printingMode");



    public static ImmutableList<IConfigBase> getConfigList()
    {
        List<IConfigBase> list = new java.util.ArrayList<>(fi.dy.masa.litematica.config.Configs.Generic.OPTIONS);
        list.add(PRINT_MODE);
        list.add(SAVE_INTERVAL);
        list.add(SCAN_BLOCKS_PER_TICK);
        list.add(PLACE_BLOCKS_PER_TICK);
        list.add(FILL_HEIGHT);
        list.add(FILL_EMPTY);

        return ImmutableList.copyOf(list);
    }
}
