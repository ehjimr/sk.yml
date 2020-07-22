package me.SuperRonanCraft.BetterRTP.player;

import com.sk89q.worldguard.bukkit.RegionContainer;
import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import me.SuperRonanCraft.BetterRTP.Main;
import me.SuperRonanCraft.BetterRTP.references.file.FileBasics;
import me.SuperRonanCraft.BetterRTP.references.worlds.*;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RTP {

    private Main pl;
    //Cache
    public HashMap<String, RTPWorld> customWorlds = new HashMap<>();
    public HashMap<String, String> overriden = new HashMap<>();
    public Default Default = new Default();
    private Random rn = new Random();
    private List<String> disabledWorlds, blockList;
    private int maxAttempts, delayTime;
    private boolean cancelOnMove;
    public HashMap<String, RTP_WORLD_TYPE> world_type = new HashMap<>();

    public RTP(Main pl) {
        this.pl = pl;
    }

    public void load() {
        Default.setup();
        FileBasics.FILETYPE config = pl.getFiles().getType(FileBasics.FILETYPE.CONFIG);
        disabledWorlds = config.getStringList("DisabledWorlds");
        maxAttempts = config.getInt("Settings.MaxAttempts");
        delayTime = config.getInt("Settings.Delay.Time");
        cancelOnMove = config.getBoolean("Settings.Delay.CancelOnMove");
        blockList = config.getStringList("BlacklistedBlocks");
        //OVER-RIDES
        try {
            overriden.clear();
            List<Map<?, ?>> override_map = config.getMapList("Overrides");
            for (Map<?, ?> m : override_map)
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    overriden.put(entry.getKey().toString(), entry.getValue().toString());
                    if (pl.getSettings().debug)
                        pl.getLogger().info("- Override '" + entry.getKey() + "' -> '" + entry.getValue() + "' added");
                }
            //for (String s : config.getConfigurationSection("Override").getKeys(false))
            //    overriden.put(s, config.getString("Override." + s));
        } catch (Exception e) {
            //No Overrides
        }

        //CUSTOM WORLDS
        try {
            customWorlds.clear();
            List<Map<?, ?>> map = config.getMapList("CustomWorlds");
            for (Map<?, ?> m : map)
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    customWorlds.put(entry.getKey().toString(), new Custom(entry.getKey().toString()));
                    if (pl.getSettings().debug)
                        pl.getLogger().info("- Custom World '" + entry.getKey() + "' registered");
                }
        } catch (Exception e) {
            //No Custom Worlds
        }

        try {
            world_type.clear();
            for (World world : Bukkit.getWorlds())
                world_type.put(world.getName(), RTP_WORLD_TYPE.NORMAL);
            List<Map<?, ?>> world_map = config.getMapList("WorldType");
            for (Map<?, ?> m : world_map)
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    if (world_type.containsKey(entry.getKey())) {
                        try {
                            RTP_WORLD_TYPE type = RTP_WORLD_TYPE.valueOf(entry.getValue().toString().toUpperCase());
                            world_type.put(entry.getKey().toString(), type);
                        } catch(IllegalArgumentException e) {
                            StringBuilder valids = new StringBuilder();
                            for (RTP_WORLD_TYPE type : RTP_WORLD_TYPE.values())
                                valids.append(type.name()).append(", ");
                            valids.replace(valids.length() - 2, valids.length(), "");
                            pl.getLogger().severe("World Type for '" + entry.getKey() + "' is INVALID '" + entry.getValue() +
                                    "'. Valid ID's are: " + valids.toString());
                            //Wrong rtp world type
                        }
                    }/* else {
                        if (pl.getSettings().debug)
                            pl.getLogger().info("- World Type failed for '" + entry.getKey() + "' is it loaded?");
                    }*/
                }
            if (pl.getSettings().debug)
                for (String world : world_type.keySet())
                    pl.getLogger().info("- World Type for '" + world + "' set to '" + world_type.get(world) + "'");
        } catch (Exception e) {
            e.printStackTrace();
            //No World Types
        }
    }

    public List<String> disabledWorlds() {
        return disabledWorlds;
    }

    @SuppressWarnings("unused")
    public List<String> getDisabledWorlds() {
        return disabledWorlds;
    }

    public void start(Player p, CommandSender sendi, String worl, List<String> biomes, boolean delay) {
        // Check overrides
        String world = worl;
        if (world == null)
            world = p.getWorld().getName();
        if (overriden.containsKey(world))
            world = overriden.get(world);
        if (sendi == p && !pl.getPerms().getAWorld(sendi, world)) {
            pl.getCmd().cooldowns.remove(p.getUniqueId());
            pl.getText().getNoPermissionWorld(p, world);
            return;
        }
        // Check disabled worlds
        if (disabledWorlds.contains(world)) {
            pl.getText().getDisabledWorld(sendi, world);
            pl.getCmd().cooldowns.remove(p.getUniqueId());
            return;
        }
        // Check if nulled
        if (Bukkit.getWorld(world) == null) {
            pl.getText().getNotExist(sendi, world);
            pl.getCmd().cooldowns.remove(p.getUniqueId());
            return;
        }
        PlayerWorld pWorld = new PlayerWorld(p, world);
        //Set all methods
        if (customWorlds.containsKey(world)) {
            RTPWorld cWorld = customWorlds.get(pWorld.getWorld());
            pWorld.setup(cWorld, cWorld.getPrice(), biomes);
        } else
            pWorld.setup(Default, Default.getPrice(), biomes);
        // Check world price
        if (!pl.getEco().charge(p, pWorld.getPrice())) {
            pl.getText().getFailedPrice(p, pWorld.getPrice());
            pl.getCmd().cooldowns.remove(p.getUniqueId());
            return;
        }
        // Delaying? Else, just go
        if (delay) {
            pl.getCmd().rtping.put(p.getUniqueId(), true);
            new Delay(sendi, pWorld, delayTime, cancelOnMove);
        } else
            tp(sendi, pWorld);
    }

    void tp(CommandSender sendi, PlayerWorld pWorld) {
        Location loc = randomLoc(pWorld);
        if (loc != null)
            sendPlayer(sendi, pWorld.getPlayer(), loc, pWorld.getPrice(), pWorld.getAttempts());
        else
            metMax(sendi, pWorld.getPlayer(), pWorld.getPrice());
    }

    private void sendPlayer(final CommandSender sendi, final Player p, final Location loc, final int price,
                            final int attempts) throws NullPointerException {
        if (sendi != p)
            checkPH(sendi, p.getDisplayName(), loc, price, false, attempts);
        if (pl.getText().getTitleSuccessChat())
            checkPH(p, p.getDisplayName(), loc, price, true, attempts);
        if (pl.getText().getTitleEnabled())
            titles(p, loc, attempts);
        try {
            //loc.getWorld().loadChunk(loc.getChunk());
            p.teleport(loc);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (pl.getText().getSoundsEnabled())
            sounds(p);
    }

    private void checkPH(CommandSender sendi, String player, Location loc, int price, boolean sameAsPlayer,
                         int attempts) {
        String x = Integer.toString(loc.getBlockX());
        String y = Integer.toString(loc.getBlockY());
        String z = Integer.toString(loc.getBlockZ());
        String world = loc.getWorld().getName();
        if (sameAsPlayer) {
            if (price == 0)
                pl.getText().getSuccessBypass(sendi, x, y, z, world, attempts);
            else
                pl.getText().getSuccessPaid(sendi, price, x, y, z, world, attempts);
        } else
            pl.getText().getOtherSuccess(sendi, player, x, y, z, world, attempts);
    }

    @SuppressWarnings({"deprecation"})
    private void titles(Player p, Location loc, int attempts) {
        // int fadeIn = pl.text.getFadeIn();
        // int stay = text.getStay();
        // int fadeOut = text.getFadeOut();
        String x = String.valueOf(loc.getBlockX());
        String y = String.valueOf(loc.getBlockY());
        String z = String.valueOf(loc.getBlockZ());
        String title = pl.getText().getTitleSuccess(p.getName(), x, y, z, attempts);
        String subTitle = pl.getText().getSubTitleSuccess(p.getName(), x, y, z, attempts);
        // player.sendMessage(Bukkit.getServer().getVersion());
        // player.sendTitle(title, subTitle, fadeIn, stay, fadeOut);
        p.sendTitle(title, subTitle);
    }

    private void sounds(Player p) {
        Sound sound = pl.getText().getSoundsSuccess();
        if (sound != null)
            p.playSound(p.getLocation(), sound, 1F, 1F);
    }

    // Compressed code for MaxAttempts being met
    private void metMax(CommandSender sendi, Player p, int price) {
        if (p == sendi)
            pl.getText().getFailedNotSafe(sendi, maxAttempts);
        else
            pl.getText().getOtherNotSafe(sendi, maxAttempts, p.getDisplayName());
        pl.getCmd().cooldowns.remove(p.getUniqueId());
        pl.getEco().unCharge(p, price);
    }

    //Get a random location depending the world type
    private Location randomLoc(PlayerWorld pWorld) {
        int borderRad = pWorld.getMaxRad();
        int minVal = pWorld.getMinRad();
        int CenterX = pWorld.getCenterX();
        int CenterZ = pWorld.getCenterZ();
        int quadrant = rn.nextInt(4);
        Player p = pWorld.getPlayer();
        World world = Bukkit.getWorld(pWorld.getWorld());
        if (pWorld.getUseWorldborder()) {
            WorldBorder border = world.getWorldBorder();
            borderRad = (int) border.getSize() / 2;
            CenterX = border.getCenter().getBlockX();
            CenterZ = border.getCenter().getBlockZ();
        }
        float yaw = p.getLocation().getYaw(), pitch = p.getLocation().getPitch();
        RTP_WORLD_TYPE world_type = RTP_WORLD_TYPE.NORMAL; //World rtp type
        /*try {
            //1.13+
            normal = !world.getBiome(0, 0).equals(Biome.valueOf("NETHER")));
        } catch (Exception e) {
            //1.8-1.12
            try {
                normal = !world.getBiome(0, 0).equals(Biome.valueOf("HELL"));
            } catch (Exception e1) {
                normal = true;
            }
        }*/
        if (this.world_type.containsKey(world.getName()))
            world_type = this.world_type.get(world.getName());
        for (int i = 0; i <= maxAttempts; i++) {
            // Get the y-coords from up top, then check if it's SAFE!
            Location loc = null;
            if (borderRad <= minVal) {
                minVal = Default.getMinRad();
                if (borderRad <= minVal)
                    minVal = 0;
            }
            switch (world_type) {
                case NETHER:
                    loc = nether(borderRad, minVal, CenterX, CenterZ, quadrant, world, pWorld, yaw, pitch); break;
                default:
                    loc = normal(borderRad, minVal, CenterX, CenterZ, quadrant, world, pWorld, yaw, pitch);
            }
            pWorld.addAttempt();
            if (loc != null && checkDepends(loc))
                return loc;
            quadrant = rn.nextInt(4);
        }
        return null;
    }

    //NORMAL
    private Location normal(int borderRad, int minVal, int CenterX, int CenterZ, int quadrant, World world,
                            PlayerWorld pWorld, Float yaw, Float pitch) {
        int x, x2, z, z2;
        Location loc;
        // Will Check is CenterZ is negative or positive, then set 2 x's
        // up for choosing up next
        z = rn.nextInt(borderRad - minVal) + CenterZ + minVal;
        z2 = -(rn.nextInt(borderRad - minVal) - CenterZ - minVal);
        // Will Check is CenterZ is negative or positive, then set 2 z's
        // up for choosing up next
        x = rn.nextInt(borderRad - minVal) + CenterX + minVal;
        x2 = -rn.nextInt(borderRad - minVal) + CenterX - minVal;
        switch (quadrant) {
            case 0: // Positive X and Z
                loc = getLocAtNormal(x, z, world, yaw, pitch, pWorld); break;
            case 1: // Negative X and Z
                loc = getLocAtNormal(x2, z2, world, yaw, pitch, pWorld); break;
            case 2: // Negative X and Positive Z
                loc = getLocAtNormal(x2, z, world, yaw, pitch, pWorld); break;
            default: // Positive X and Negative Z
                loc = getLocAtNormal(x, z2, world, yaw, pitch, pWorld);
        }
        return loc;
    }

    private Location getLocAtNormal(int x, int z, World world, Float yaw, Float pitch, PlayerWorld pWorld) {
        Block b = world.getHighestBlockAt(x, z);
        //System.out.println("-----------");
        //System.out.println(b.getType().isSolid() + " " + b.getType().name());
        if (b.getType() == Material.AIR || !b.getType().isSolid()) { //1.15.1 or less
            int y = world.getHighestBlockYAt(x, z);
            b = world.getBlockAt(x, y - 1, z);
        }
        //System.out.println(b.getType().isSolid() + " " + b.getType().name());
        if (b.getY() > 0 && !badBlock(b.getType().name(), x, z, pWorld.getWorld(), pWorld.getBiomes()))
            return new Location(world, (x + 0.5), b.getY() + 1, (z + 0.5), yaw, pitch);
        return null;
    }

    //NETHER
    private Location nether(int borderRad, int minVal, int CenterX, int CenterZ, int quadrant, World world,
                            PlayerWorld pWorld, Float yaw, Float pitch) {
        int x, x2, z, z2;
        Location loc;
        // Will Check is CenterZ is negative or positive, then set 2 x's
        // up for choosing up next
        z = rn.nextInt((borderRad) - minVal) + CenterZ + minVal;
        z2 = -(rn.nextInt(borderRad - minVal) - CenterZ - minVal);
        // Will Check is CenterZ is negative or positive, then set 2 z's
        // up for choosing up next
        x = rn.nextInt(borderRad - minVal) + CenterX + minVal;
        x2 = -rn.nextInt(borderRad - minVal) + CenterX - minVal;
        switch (quadrant) {
            case 0: // Positive X and Z
                loc = getLocAtNether(x, z, world, yaw, pitch, pWorld); break;
            case 1: // Negative X and Z
                loc = getLocAtNether(x2, z2, world, yaw, pitch, pWorld); break;
            case 2: // Negative X and Positive Z
                loc = getLocAtNether(x2, z, world, yaw, pitch, pWorld); break;
            default: // Positive X and Negative Z
                loc = getLocAtNether(x, z2, world, yaw, pitch, pWorld);
        }
        return loc;
    }

    private Location getLocAtNether(int x, int z, World world, Float yaw, Float pitch, PlayerWorld pWorld) {
        for (int y = 0; y < world.getMaxHeight(); y++) {
            Block b = world.getBlockAt(x, y, z);
            if (b.getType().equals(Material.AIR) || !b.getType().isSolid()) {
                String block = world.getBlockAt(x, y - 1, z).getType().name();
                if (!b.getType().isSolid()) { //Block is not a solid (ex: lava, water...)
                    String block_in = b.getType().name();
                    if (badBlock(block_in, x, z, pWorld.getWorld(), pWorld.getBiomes()))
                        return null;
                }
                if (!badBlock(block, x, z, pWorld.getWorld(), pWorld.getBiomes()))
                    return new Location(world, (x + 0.5), y, (z + 0.5), yaw, pitch);
            }
        }
        return null;
    }

    @SuppressWarnings("all")
    private boolean checkDepends(Location loc) {
        try {
            if (pl.getSettings().getsDepends().isWorldguard()) {
                WorldGuardPlugin plugin = WGBukkit.getPlugin();
                RegionContainer container = plugin.getRegionContainer();
                RegionManager regions = container.get(loc.getWorld());
                // Check to make sure that "regions" is not null
                return regions.getApplicableRegions(loc).size() == 0;
            }
            return !pl.getSettings().getsDepends().isGriefprevention() || GriefPrevention.instance.dataStore.getClaimAt(loc, true, null) == null;
        } catch (NoClassDefFoundError e) {
            return true;
        }
    }

    // Bad blocks, or good block and bad biome
    private boolean badBlock(String block, int x, int z, String world, List<String> biomes) {
        for (String currentBlock : blockList) //Check Block
            if (currentBlock.toUpperCase().equals(block))
                return true;
        //Check Biomes
        if (biomes == null || biomes.isEmpty())
            return false;
        String biomeCurrent = Bukkit.getWorld(world).getBiome(x, z).name();
        for (String biome : biomes)
            if (biomeCurrent.toUpperCase().contains(biome.toUpperCase()))
                return false;
        return true;
        //FALSE MEANS NO BAD BLOCKS WHERE FOUND!
    }
}