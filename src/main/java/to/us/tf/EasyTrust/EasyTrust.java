package to.us.tf.EasyTrust;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Created by RoboMWM on 1/1/2017.
 * Clearly not my best work because I got lazy. :/
 */
public class EasyTrust extends JavaPlugin {
    GriefPrevention gp;

    final String EmptyList = ChatColor.RED + "Your EasyTrust list is empty!";
    final String successfulAdd = ChatColor.GREEN + " was added to your EasyTrust list.";
    final String successfulRemove = ChatColor.GREEN + " was removed from your EasyTrust list.";
    final String failedRemove = ChatColor.RED + " could not be removed from your EasyTrust list. Either the name is not valid, or the player has not logged in recently.";
    final String failedAdd = ChatColor.RED + " could not be added to your EasyTrust list. Either the name is not valid, or the player has not logged in recently.";
    final String EasyTrustClaimHelp = ChatColor.GOLD + "/easytrust claim " + ChatColor.YELLOW + ChatColor.ITALIC + "trustlevel" +
            ChatColor.RESET + " - Trusts everyone in your EasyTrust list with the specified trust level to your claim (or all your claims, if standing outside them).\n" +
            ChatColor.YELLOW + "Trust levels: trust, containertrust, accesstrust, permissiontrust";
    final String EasyTrustHelp = ChatColor.translateAlternateColorCodes('&',
            "&6/easytrust list &r- Lists players in your EasyTrust list\n" +
                    "&6/easytrust claim &r- Trusts players on your list to your claim.\n" +
                    "&6/easytrust add &e&oname &r- Adds a player to your EasyTrust list\n" +
            "&6/easytrust remove &e&oname &r- Removes a player from your EasyTrust list");


    YamlConfiguration storage;
    LinkedHashMap<String, List<String>> PlayersWithAList = new LinkedHashMap<>();

    public void onEnable()
    {
        saveConfig(); //Creates data folder for me if none exists

        gp = (GriefPrevention)getServer().getPluginManager().getPlugin("GriefPrevention");
        //I use .data extension cuz reasons that have to do with how I automatically manage .yml files on my server so yea...
        //Not like they're supposed to touch this file anyways.
        File storageFile = new File(getDataFolder(), "storage.data");
        if (!storageFile.exists())
        {
            try
            {
                storageFile.createNewFile();
            }
            catch (IOException e)
            {
                this.getLogger().severe("Could not create storage.data! Since I'm lazy, there currently is no \"in memory\" option. Will now disable along with a nice stack trace for you to bother me with:");
                e.printStackTrace();
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        storage = YamlConfiguration.loadConfiguration(storageFile);

        ConfigurationSection PlayersWithAListSection = storage.getConfigurationSection("PlayersWithAList");
        if (PlayersWithAListSection == null) //Create section if it doesn't exist
        {
            storage.set("PlayersWithAList", new LinkedHashMap<String, String>());
            return; //nothing to load
        }

        //Load data into plugin memory

        for (String uuid : PlayersWithAListSection.getKeys(false))
        {
            Set<String> UUIDsOnTheList = new LinkedHashSet<>();
            for (String UUIDOnList : PlayersWithAListSection.getStringList(uuid))
                UUIDsOnTheList.add(UUIDOnList);
            PlayersWithAList.put(uuid, new LinkedList<>(UUIDsOnTheList));
        }
    }

    public void onDisable()
    {
        saveData();
    }

    void saveData()
    {
        File storageFile = new File(getDataFolder(), "storage.data");
        if (storage != null)
        {
            try
            {
                storage.set("PlayersWithAList", PlayersWithAList);
                storage.save(storageFile);
            }
            catch (IOException e) //really
            {
                e.printStackTrace();
            }
        }
    }

    public void addToList(String playerUUID, String targetUUID)
    {
        //If player already has a EasyTrust list, just add to this list.
        if (PlayersWithAList.containsKey(playerUUID))
        {
            if (!PlayersWithAList.get(playerUUID).contains(targetUUID)) //Because I'm using a list instead of a set now...
                PlayersWithAList.get(playerUUID).add(targetUUID);
        }
        //Otherwise, if never used a list before, create it
        else
        {
            List<String> UUIDsOnTheList = new LinkedList<>();
            UUIDsOnTheList.add(targetUUID);
            PlayersWithAList.put(playerUUID, UUIDsOnTheList);
            getLogger().info("Added " + UUIDsOnTheList + " to " + playerUUID);
            getLogger().info(String.valueOf(PlayersWithAList.containsKey(playerUUID)));
        }
        saveData();
    }

    /**
     * @return true if the player was removed, false if the player was already removed
     */
    public boolean removeFromList(String playerUUID, String targetUUID)
    {
        if (PlayersWithAList.containsKey(playerUUID))
            return PlayersWithAList.get(playerUUID).remove(targetUUID);
        return false;
    }

    /**
     * Converts UUIDs on a player's list to player names. Automatically removes UUIDs that don't resolve to names.
     * @param playerUUID
     * @return a set of player names, or null if list does not exist/is empty
     */
    public Set<String> uuidsToPlayerNames(String playerUUID)
    {
        if (!PlayersWithAList.containsKey(playerUUID))
            return null;
        List<String> uuids = PlayersWithAList.get(playerUUID);
        Set<String> names = new LinkedHashSet<>();
        boolean cleanup = false;

        for (String uuidString : uuids)
        {
            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(UUID.fromString(uuidString));
            if (offlinePlayer.getName() == null || offlinePlayer.getName().isEmpty())
            {
                //Schedule removal of unresolvable players from player's EasyTrust list
                cleanup = true;
                continue;
            }
            names.add(offlinePlayer.getName());
        }
        if (cleanup)
            scheduleCleanup(playerUUID);
        if (names.isEmpty())
            return null;

        return names;
    }

    /**
     * Lists players on the EasyTrust list
     * @param playerUUID list to get
     * @return a comma-delimited string of players on the player's list, or null if nobody's on the list
     */
    public String listPlayersOnList(String playerUUID)
    {
        Set<String> names = uuidsToPlayerNames(playerUUID);
        if (names == null)
            return null;
        StringBuilder formattedNames = new StringBuilder();
        for (String name : names)
        {
            formattedNames.append(", ");
            formattedNames.append(name);
        }
        formattedNames.delete(0, 2);
        return formattedNames.toString();
    }

    /**
     * Adds a player to a player's EasyTrust list
     * @param player list to add to
     * @param targetName name of player to add
     * @return true if succeeded, false if failed (invalid name, etc.)
     */
    public boolean addNameToList(Player player, String targetName)
    {
        OfflinePlayer offlinePlayer = gp.resolvePlayerByName(targetName);
        if (offlinePlayer == null)
            return false;

        String uuid = offlinePlayer.getUniqueId().toString();
        addToList(player.getUniqueId().toString(), uuid);
        return true;
    }

    /**
     * Removes a player from a player's EasyTrust list
     * @param player list to remove from
     * @param targetName name of player to remove
     * @return true if succeeded, false if failed (invalid name, etc.)
     */
    public boolean removeNameFromList(Player player, String targetName)
    {
        OfflinePlayer offlinePlayer = gp.resolvePlayerByName(targetName);
        if (offlinePlayer == null)
            return false;

        String uuid = offlinePlayer.getUniqueId().toString();
        removeFromList(player.getUniqueId().toString(), uuid);
        return true;
    }

    /**
     * Cleans up unresolvable UUIDs in an EasyTrust list
     * @param playerUUID
     */
    private void scheduleCleanup(String playerUUID)
    {
        new BukkitRunnable()
        {
            public void run()
            {
                if (!PlayersWithAList.containsKey(playerUUID))
                    return;
                List<String> newUUIDList = new LinkedList<>();
                for (String uuidString : PlayersWithAList.get(playerUUID))
                {
                    OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(UUID.fromString(uuidString));
                    if (offlinePlayer.getName() != null && !offlinePlayer.getName().isEmpty())
                    {
                        newUUIDList.add(uuidString);
                    }
                }
                PlayersWithAList.put(playerUUID, newUUIDList);
            }
        }.runTaskLater(this, 100L);
    }

    /**
     * Executes the appropriate trust commands
     * @return false if player's EasyTrust list is empty.
     */
    private boolean trustPlayers(@Nonnull Player player, @Nonnull String trustCommand)
    {
        Set<String> targetPlayers = uuidsToPlayerNames(player.getUniqueId().toString());
        if (targetPlayers == null)
            return false;
        for (String targetName : targetPlayers)
        {
            boolean success = player.performCommand(trustCommand + " " + targetName);
            if (!success)
                return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        boolean success = handleDaCommand(sender, cmd, label, args);
        if (!success)
            sender.sendMessage(EasyTrustHelp);
        return success;
    }

    private boolean handleDaCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if (!(sender instanceof Player))
            return false;

        //Someday, yes, I will make a separate command-handler-thingy ok. Someday. Soon.
        if (!cmd.getName().equalsIgnoreCase("easytrust"))
            return false;

        if (args.length < 1)
            return false;

        Player player = (Player)sender;
        String command = args[0];

        if (command.equalsIgnoreCase("list"))
        {
            String easyTrustList = listPlayersOnList(player.getUniqueId().toString());
            if (easyTrustList == null)
                player.sendMessage(EmptyList);
            else
                player.sendMessage("Your EasyTrust list: " + easyTrustList);
            return true;
        }

        /**
         * performing the trusting
         */
        if (command.equalsIgnoreCase("claim"))
        {
            if (args.length < 2 || !trustPlayers(player, args[1]))
                player.sendMessage(EasyTrustClaimHelp);
            return true;
        }


        if (args.length < 2)
            return false;

        if (command.equalsIgnoreCase("add"))
        {
            for (int i = 1; i < args.length; i++)
            {
                if (addNameToList(player, args[i]))
                    player.sendMessage(args[i] + successfulAdd);
                else
                    player.sendMessage(args[i] + failedAdd);
            }
            return true;
        }

        if (command.equalsIgnoreCase("remove"))
        {
            for (int i = 1; i < args.length; i++)
            {
                if (removeNameFromList(player, args[i]))
                    player.sendMessage(args[i] + successfulRemove);
                else
                    player.sendMessage(args[i] + failedRemove);
            }
            return true;
        }
        return false;
    }
}