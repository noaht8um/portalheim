package dev.noaht8um.portalheim;

import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PortalheimPlugin extends JavaPlugin {
    private PortalManager portalManager;
    private Object squaremapIntegration;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        portalManager = new PortalManager(this, new PortalDetector(), new PortalStorage(this));
        portalManager.enable();
        enableSquaremapIntegration();

        getServer().getPluginManager().registerEvents(new PortalListener(this, portalManager), this);

        PluginCommand command = Objects.requireNonNull(getCommand("portalheim"), "portalheim command missing from plugin.yml");
        PortalCommand portalCommand = new PortalCommand(this, portalManager);
        command.setExecutor(portalCommand);
        command.setTabCompleter(portalCommand);

        portalManager.armPersistence();
    }

    @Override
    public void onDisable() {
        if (squaremapIntegration != null) {
            invokeSquaremap("disable");
        }
        if (portalManager != null) {
            portalManager.disable();
        }
    }

    public void debug(String message) {
        if (getConfig().getBoolean("debug-logging", false)) {
            getLogger().info("[debug] " + message);
        }
    }

    private void enableSquaremapIntegration() {
        if (getServer().getPluginManager().getPlugin("squaremap") == null) {
            return;
        }

        try {
            Class<?> integrationClass = Class.forName("dev.noaht8um.portalheim.SquaremapIntegration");
            squaremapIntegration = integrationClass
                .getConstructor(PortalheimPlugin.class, PortalManager.class)
                .newInstance(this, portalManager);
            portalManager.setPortalUpdateHook(() -> invokeSquaremap("refresh"));
            invokeSquaremap("enable");
        } catch (ReflectiveOperationException | LinkageError exception) {
            squaremapIntegration = null;
            getLogger().warning("squaremap is installed but its API could not be loaded. Portal markers will stay disabled.");
            debug("squaremap bootstrap failure: " + exception);
        }
    }

    private void invokeSquaremap(String methodName) {
        if (squaremapIntegration == null) {
            return;
        }

        try {
            squaremapIntegration.getClass().getMethod(methodName).invoke(squaremapIntegration);
        } catch (ReflectiveOperationException | LinkageError exception) {
            getLogger().warning("squaremap integration call failed during " + methodName + ". Disabling portal markers.");
            debug("squaremap invocation failure: " + exception);
            squaremapIntegration = null;
            portalManager.setPortalUpdateHook(() -> {
            });
        }
    }
}
