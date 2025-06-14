package me.aleksilassila.litematica.printer;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.config.Hotkeys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

public class Printer {
    public static final Logger logger = LogManager.getLogger(PrinterReference.MOD_ID);
    @NotNull
    public final ClientPlayerEntity player;
    public final MinecraftClient client;
    private int cachedIndex = 0;
    private Region currentRegion;
    private boolean scanComplete = false;
    private boolean scanning = false;
    private static final int BLOCKS_PER_SCAN_TICK = 500; // Tune as needed


    private Map<Region, List<BlockPos>> regionPositions = new HashMap<>();
    private List<Region> regions = new ArrayList<>(); // Store regions from SimpleArea
    private List<BlockPos> cachedPositions = new ArrayList<>();




    public Printer(@NotNull MinecraftClient client, @NotNull ClientPlayerEntity player) {
        this.player = player;
        this.client = client;
    }

    public boolean onGameTick() {
        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();


        if (worldSchematic == null)
            return false;

        // If hotkey is pressed, we toggle the printer
        if(Hotkeys.PRINT.getKeybind().isPressed()) {
            // Reset 
            cachedPositions = new ArrayList<>();
            cachedIndex = 0;
            currentRegion = null;
            regions = new ArrayList<>();
            regionPositions.clear();
            logger.info("Printer reset.");
            scanComplete = false;
            scanning = false;
        }

        if (!Configs.PRINT_MODE.getBooleanValue())
            return false;

        // Start or continue scanning if not complete
        if (!scanComplete) {
            if (!scanning) {
                startReachablePositionsScan();
            }
            scanReachablePositionsTick();
            return false;
        }
        PlayerAbilities abilities = player.getAbilities();
        if (!abilities.allowModifyWorld)
            return false;

        // Process one block per tick
        if(!regions.isEmpty() && currentRegion != null){
            List<BlockPos> blocksInRegion = regionPositions.get(currentRegion);
            if(cachedIndex >= blocksInRegion.size()){
                // Set next region
                regions.removeFirst();
                currentRegion = null;
                if(regions.isEmpty()) return true;
                currentRegion = regions.getFirst();
                cachedIndex = 0;
                return true;
            }
            player.sendMessage(
                    net.minecraft.text.Text.literal("Placing schematic... "+cachedIndex + " out of "+blocksInRegion.size() + " (region: "+currentRegion.getName()+")"),
                    true
            );
            BlockPos position = blocksInRegion.get(cachedIndex++);
            place(position);
            return true;
        }else if(!cachedPositions.isEmpty() && cachedIndex < cachedPositions.size()){
            player.sendMessage(
                    net.minecraft.text.Text.literal("Placing schematic... "+cachedIndex + " out of "+cachedPositions.size()),
                    true
            );
            BlockPos position = cachedPositions.get(cachedIndex++);
            place(position);
            return true;
        }

        return false;
    }

    private void place(BlockPos pos){
        WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
        BlockState stateSchematic = world.getBlockState(pos);
        BlockState stateClient = client.world.getBlockState(pos);
        
        // Skip if already matches
        if (stateClient.equals(stateSchematic)) {
            return;
        }
        
        if (stateSchematic.isAir()) {
            return;
        }


        placeBlockWithCommand(pos, stateSchematic);
    }

    private void placeBlockWithCommand(BlockPos pos, BlockState targetState) {
        if (!client.isIntegratedServerRunning()) {
            logger.warn("Command-based placement only works in single-player!");
            return;
        }
        
        // Convert BlockState to command string
        String blockStateString = blockStateToString(targetState);
        String command = String.format("setblock %d %d %d %s", pos.getX(), pos.getY(), pos.getZ(), blockStateString);
        
        // Send command
        player.networkHandler.sendChatCommand(command);
        logger.info("Executed command: /{}", command);
    }

    private String blockStateToString(BlockState state) {
        // Get the block's registry name
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        String blockName = blockId.toString();
        // Build properties string
        StringBuilder properties = new StringBuilder();
        for (net.minecraft.state.property.Property<?> property : state.getProperties()) {
            if (properties.length() > 0) {
                properties.append(",");
            }
            properties.append(property.getName()).append("=").append(state.get(property).toString());
        }
        
        if (properties.length() > 0) {
            return blockName + "[" + properties.toString() + "]";
        } else {
            return blockName;
        }
    }   


    private void startReachablePositionsScan() {
        logger.info("Scanning for reachable blocks...");
        scanning = true;
        scanComplete = false;
        cachedPositions.clear();
        regionPositions.clear();
        regions.clear();
        cachedIndex = 0;

        // Use SchematicPlacementManager and SchematicPlacement
        SchematicPlacementManager placementManager =
            DataManager.getSchematicPlacementManager();
        SchematicPlacement placement =
            placementManager.getSelectedSchematicPlacement();

        if (placement == null) {
            logger.warn("No schematic placement selected!");
            player.sendMessage(
                net.minecraft.text.Text.literal("Please enable enclosing box for this placement"),
                false
            );
            // Only reset placement scan fields
            scanPlacementOrigin = null;
            scanPlacementBox = null;
            scanPlacementCurrentPos = null;
            return;
        }

        BlockPos origin = placement.getOrigin();
        logger.info("Placement origin: {}", origin);
        Box enclosingBox = placement.getEclosingBox();
        if(enclosingBox == null){
            logger.warn("Enclosing box not found!");
            // Only reset placement scan fields
            scanPlacementOrigin = null;
            scanPlacementBox = null;
            scanPlacementCurrentPos = null;
            return;
        }

        // Get subregions

        var _regions = placement.getAllSubRegionsPlacements();
        var _boxes = placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.ANY);
        if(_regions != null){
            logger.info("Found {} regions in placement. ", _regions.size());

            for (var _sr : _regions) {
                logger.info("Region: {}", _sr.getName());
                var _box = _boxes.get(_sr.getName());
                var _region = new Region(_sr.getName(),_box.copy());
                regionPositions.put(_region, new ArrayList<>());
                regions.add(_region);
            }
        }



        // Log placement details
        logger.info("Placement box: min={}, max={}, size={}", 
            enclosingBox.getPos1(), enclosingBox.getPos2(), enclosingBox.getSize());



        // Prepare for incremental scan
        scanPlacementOrigin = origin;
        scanPlacementBox = enclosingBox;
        scanPlacementCurrentPos = null;
    }

    // New fields for incremental placement scan
    private BlockPos scanPlacementOrigin = null;
    private Box scanPlacementBox = null;
    private BlockPos scanPlacementCurrentPos = null;

    private void scanReachablePositionsTick() {
        int blocksScanned = 0;

        if (scanPlacementBox == null || scanPlacementOrigin == null) {
            scanComplete = true;
            scanning = false;
            return;
        }

        BlockPos min = scanPlacementBox.getPos1();
        BlockPos max = scanPlacementBox.getPos2();

        int expand = 10;
        int minX = Math.min(min.getX(), max.getX()) - expand;
        int minY = Math.min(min.getY(), max.getY()) - expand;
        int minZ = Math.min(min.getZ(), max.getZ()) - expand;
        int maxX = Math.max(min.getX(), max.getX()) + expand;
        int maxY = Math.max(min.getY(), max.getY()) + expand;
        int maxZ = Math.max(min.getZ(), max.getZ()) + expand;

        // Initialize scan position if needed
        if (scanPlacementCurrentPos == null) {
            scanPlacementCurrentPos = new BlockPos(minX, minY, minZ);
        }

        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
        net.minecraft.world.World world = player.getWorld();
        var layer = DataManager.getRenderLayerRange();

        // Scan positions within the box
        while (blocksScanned < BLOCKS_PER_SCAN_TICK && scanPlacementCurrentPos != null) {
            BlockPos pos = scanPlacementCurrentPos;
            if(!layer.isPositionWithinRange(pos)){
                continue;
            }

            SchematicBlockState state = new SchematicBlockState(player.getWorld(), worldSchematic, pos);
            if (!state.targetState.isAir() && !state.currentState.equals(state.targetState))
            {
                boolean addedToRegion = false;
                for (Region region : regions) {
                    if (region.containsPosition(pos)) {
                        regionPositions.get(region).add(pos);
                        addedToRegion = true;
                        logger.info("Added block at pos {} to region {}", pos, region.getName());
                        break;
                    }
                }
                if (!addedToRegion) {
                    logger.info("Block was not in any region, added to global list. Block: {}", pos);
                    cachedPositions.add(pos);
                }
            }
            blocksScanned++;

            // Increment position (x, then z, then y)
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            if (x <= maxX) {
                scanPlacementCurrentPos = new BlockPos(x + 1, y, z);
            } else if (z <= maxZ) {
                scanPlacementCurrentPos = new BlockPos(minX, y, z + 1);
            } else if (y <= maxY) {
                scanPlacementCurrentPos = new BlockPos(minX, y + 1, minZ);
            } else {
                scanPlacementCurrentPos = null; // Finished this placement
            }
        }

        // Print progress to chat and log
        String progressMsg = String.format(
            "Scanning... Found %d blocks so far. Last scanned: %s",
            cachedPositions.size(),
            cachedPositions.isEmpty() ? "none" : cachedPositions.get(cachedPositions.size() - 1)
        );
        player.sendMessage(
            net.minecraft.text.Text.literal(progressMsg),
            true
        );
        logger.info(progressMsg);

        // If finished all positions in placement
        if (scanPlacementCurrentPos == null) {
            if(!regions.isEmpty()){
                currentRegion = regions.getFirst();
            }
            scanComplete = true;
            scanning = false;
            logger.info("Scan complete. Found {} blocks within placement.", cachedPositions.size());
            player.sendMessage(
                net.minecraft.text.Text.literal("Scan complete. Found " + cachedPositions.size() + " blocks."),
                false
            );
        }
    }

    public static String getModVersionString(String modId)
    {
        for (net.fabricmc.loader.api.ModContainer container : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods())
        {
            if (container.getMetadata().getId().equals(modId))
            {
                return container.getMetadata().getVersion().getFriendlyString();
            }
        }

        return "?";
    }

    public static void printDebug(String key, Object... args)
    {
        if (Configs.PRINT_DEBUG.getBooleanValue())
        {
            logger.info(key, args);
        }
    }
}

