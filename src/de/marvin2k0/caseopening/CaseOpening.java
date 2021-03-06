package de.marvin2k0.caseopening;

import de.marvin2k0.caseopening.utils.Locations;
import net.minecraft.server.v1_15_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_15_R1.CraftWorld;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class CaseOpening extends JavaPlugin implements Listener
{
    private static ArrayList<Location> inUse = new ArrayList<>();
    private HashMap<String, Integer> materials;
    private ArrayList<String> names;
    private Random random;

    @Override
    public void onEnable()
    {
        Locations.setUp(this);

        random = new Random();
        materials = new HashMap<>();
        names = new ArrayList<>();

        getConfig().options().copyDefaults(true);
        getConfig().addDefault("items.DIAMOND.amount", 1);
        saveConfig();

        loadItems();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("caseitem").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player))
        {
            sender.sendMessage("§cNur fuer Spieler!");
            return true;
        }

        Player player = (Player) sender;

        if (player.getItemInHand().getType() == Material.AIR)
        {
            player.sendMessage("§7Du musst ein §cItem in der Hand §7haben.");
            return true;
        }

        ItemStack item = player.getItemInHand();

        addItem(item);
        player.sendMessage("§aDas Item wurde erfolgreich gespeichert!");
        return true;
    }

    private void addItem(ItemStack item)
    {
        getConfig().set("items." + item.getType().toString() + ".amount", item.getAmount());
        saveConfig();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event)
    {
        if (event.getBlock().getType() == Material.ENDER_CHEST)
        {
            Player player = event.getPlayer();
            Location loc = event.getBlock().getLocation();

            Collection<org.bukkit.entity.Entity> armorstand = loc.getWorld().getNearbyEntities(loc, 1, 1, 1);

            for (org.bukkit.entity.Entity e : armorstand)
            {
                if (e instanceof  ArmorStand)
                    e.remove();
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event)
    {
        Player player = event.getPlayer();

        if (event.hasBlock() && event.getClickedBlock().getType() == Material.ENDER_CHEST && event.hasItem() && event.getItem().getType() == Material.BLAZE_ROD && player.hasPermission("case.create"))
        {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();

            Locations.setLocation("chests." + UUID.randomUUID(), loc);
            ArmorStand armorStand = (ArmorStand) loc.getWorld().spawnEntity(loc.add(0.5, -0.25, 0.5), EntityType.ARMOR_STAND);
            armorStand.setGravity(false);
            armorStand.setVisible(false);
            armorStand.setInvulnerable(true);
            armorStand.setCustomName("§aTägliche Belohnung");
            armorStand.setCustomNameVisible(true);

            player.sendMessage("§7Die Case wurde erfolgreich erstell!");
            return;
        }


        if (event.hasBlock() && event.getClickedBlock().getType() == Material.ENDER_CHEST && event.getAction() == Action.RIGHT_CLICK_BLOCK && isCase(event.getClickedBlock().getLocation()))
        {
            event.setCancelled(true);


            Location enderChestLocation = event.getClickedBlock().getLocation();

            if (canPlay(player))
            {
                if (player.getInventory().firstEmpty() == -1)
                {
                    player.sendMessage("§cDein Inventar ist voll!");
                    return;
                }

                if (!inUse(enderChestLocation))
                {
                    getConfig().set(player.getUniqueId().toString(), System.currentTimeMillis());
                    saveConfig();

                    inUse.add(enderChestLocation);
                    playEffect(player, enderChestLocation);
                }
                else
                {
                    player.sendMessage("§cDiese Kiste ist gerade in Verwendung!");
                }
            }
            else
            {
                long milliseconds = getConfig().getLong(player.getUniqueId().toString());
                long left = milliseconds + 1000 * 60 * 60 * 24 - System.currentTimeMillis();
                long stunden = left / 1000 / 60 / 60;
                long minuten = (left / 1000 / 60) % 60;

                player.sendMessage("§7Du kannst erst wieder in §c" + stunden + " Stunden §7und §c" + minuten + " Minuten §7spielen!");
            }
        }
    }

    private void playEffect(Player player, Location chestLocation)
    {
        Location loc = chestLocation.clone().add(0.5, 0, 0.5);

        new BukkitRunnable()
        {
            double radius = 0.75;
            double t = 0;
            int timer = 0;

            @Override
            public void run()
            {

                t += Math.PI / 8;

                double x = radius * Math.cos(t);
                double y = t * 0.1;
                double z = radius * Math.sin(t);

                loc.add(x, y, z);
                loc.getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, loc, 3, 0.5, 1, 0.5, 0);
                loc.subtract(x, y, z);
                timer++;

                if (timer >= 20 * 3)
                {
                    spawnRandomItem(player, chestLocation);
                    this.cancel();
                }
            }

        }.runTaskTimer(this, 0, 1);
    }

    private void spawnRandomItem(Player player, Location enderChestLocation)
    {
        changeEnderChestState(enderChestLocation, true);
        spawnItem(player, enderChestLocation);
    }

    private void spawnItem(Player player, Location itemLocation)
    {
        ItemStack price = getRandomMaterial();

        Item item = itemLocation.getWorld().dropItem(itemLocation.add(0.5, 1, 0.5), price);
        item.setVelocity(new Vector(0, 0, 0));
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setGravity(false);
        item.setCustomName(getItemName(item.getItemStack().getType().toString()));
        item.setCustomNameVisible(true);

        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                item.remove();
                inUse.remove(itemLocation.subtract(0.5, 1, 0.5));
                changeEnderChestState(itemLocation, false);
                giveItem(player, price);
            }
        }.runTaskLater(this, 5 * 20);
    }

    private void giveItem(Player player, ItemStack item)
    {
        player.getInventory().addItem(item);
    }

    private boolean isCase(Location loc)
    {
        if (!getConfig().isSet("chests"))
            return false;

        Map<String, Object> section = getConfig().getConfigurationSection("chests").getValues(false);

        for (Map.Entry<String, Object> entry : section.entrySet())
        {
            if (Locations.get("chests." + entry.getKey()).distance(loc) <= 1)
                return true;
        }

        return false;
    }

    private String getItemName(String str)
    {
        str = str.toLowerCase().replace("_", " ");
        String name = "";

        for (String word : str.split(" "))
        {
            name += word.substring(0, 1).toUpperCase() + word.substring(1) + " ";
        }
        return "§b" + name.trim();
    }

    private void loadItems()
    {
        try
        {
            for (Map.Entry<String, Object> entry : getConfig().getConfigurationSection("items").getValues(false).entrySet())
            {
                int amount = 1;

                if (getConfig().isSet("items." + entry.getKey() + ".amount"))
                {
                    amount = getConfig().getInt("items." + entry.getKey() + ".amount");
                }

                System.out.println(entry.getKey() + " " + amount);

                materials.put(entry.getKey(), amount);
                names.add(entry.getKey());
            }
        }
        catch (Exception e)
        {
            Bukkit.getConsoleSender().sendMessage("§4BITTE FUEGE MINDESTENS EIN ITEM HINZU!");
        }
    }

    private ItemStack getRandomMaterial()
    {
        Material material = Material.getMaterial(names.get(random.nextInt(names.size())));

        ItemStack item = new ItemStack(material);
        item.setAmount(materials.get(material.toString()));

        return item;
    }

    private boolean inUse(Location loc)
    {
        for (Location l : inUse)
        {
            if (l.distance(loc) <= 1.224744871391589)
                return true;
        }

        return false;
    }

    public boolean canPlay(Player player)
    {
        if (!getConfig().isSet(player.getUniqueId().toString()))
            return true;

        long time = getConfig().getLong(player.getUniqueId().toString());
        long left = (time + (1000 * 60 * 60 * 24)) - System.currentTimeMillis();

        return left <= 0;
    }

    private void changeEnderChestState(Location chestLocation, boolean open)
    {
        World world = ((CraftWorld) chestLocation.getWorld()).getHandle();
        BlockPosition position = new BlockPosition(chestLocation.getX(), chestLocation.getY(), chestLocation.getZ());

        TileEntityEnderChest tileChest = (TileEntityEnderChest) world.getTileEntity(position);

        if (open)
            tileChest.d();
        else
            tileChest.f();
    }
}
