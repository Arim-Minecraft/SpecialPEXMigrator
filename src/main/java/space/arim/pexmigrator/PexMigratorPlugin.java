package space.arim.pexmigrator;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.InheritanceNode;

public class PexMigratorPlugin extends JavaPlugin {

	private ConfigurationSection users;
	
	private final HashMap<UUID, String> uuids = new HashMap<>();
	private final HashMap<UUID, Collection<String>> groups = new HashMap<>();
	
	@Override
	public void onEnable() {
		reloadSource();
	}
	
	private void debug(String msg) {
		getLogger().info(msg);
	}
	
	private void warn(String msg) {
		getLogger().warning(msg);
	}
	
	private String remapGroupName(String rawGroup) {
		if (rawGroup.startsWith("kit")) {
			return "kitpvp" + rawGroup.substring(3);
		} else if (rawGroup.startsWith("tntkit")) {
			return "tntwars" + rawGroup.substring(6);
		}
		return rawGroup;
	}
	
	private void reloadSource() {
		YamlConfiguration sourceYaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "permissions.yml"));
		users = Objects.requireNonNull(sourceYaml.getConfigurationSection("users"));
	}
	
	private void prepare() {
		uuids.clear();
		groups.clear();
		for (String uuidString : users.getKeys(false)) {

			ConfigurationSection section = users.getConfigurationSection(uuidString);
			UUID uuid = UUID.fromString(uuidString);

			debug("UUID=" + uuid);
			String name = null;
			if (section.isConfigurationSection("options")) {
				ConfigurationSection options = section.getConfigurationSection("options");
				if (options.contains("name")) {
					name = options.getString("name");
				}
			}
			if (name != null) {
				debug("Name=" + name);
				uuids.put(uuid, name);
			} else {
				warn("No name found!!!");
			}

			if (section.contains("group")) {
				debug("Found groups of user");
				List<String> groups = section.getStringList("group");
				Set<String> result = new HashSet<>();
				groups.forEach((group) -> {
					debug("Found group: " + group);
					String remapped = remapGroupName(group);
					debug("Remapped group: " + remapped);
					result.add(remapped);
				});
				this.groups.put(uuid, result);
			}
		}
	}
	
	private void execute() {
		UserManager um = LuckPermsProvider.get().getUserManager();
		//uuids.forEach((uuid, name) -> um.savePlayerData(uuid, name));
		groups.forEach((uuid, groupSet) -> {
			um.loadUser(uuid, uuids.get(uuid)).thenAccept((user) -> {
				groupSet.forEach((group) -> {
					user.data().add(InheritanceNode.builder(group).value(true).build());
				});
				um.saveUser(user);
			});
		});
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (sender.hasPermission("pexmigrator.use")) {
			if (args.length > 0) {
				switch (args[0].toLowerCase()) {
				case "reloadsource":
					reloadSource();
					return true;
				case "prepare":
					prepare();
					return true;
				case "execute":
					execute();
					return true;
				default:
					break;
				}
			}
			sendMessage(sender, "&cUsage: /pexmigrator <reloadsource|prepare|execute>. You must specify an action.");
		} else {
			sendMessage(sender, "&cSorry, you cannot use this.");
		}
		return true;
	}

	private void sendMessage(CommandSender sender, String message) {
		sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
	}

}
