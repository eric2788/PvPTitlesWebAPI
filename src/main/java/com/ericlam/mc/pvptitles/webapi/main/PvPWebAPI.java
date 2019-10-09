package com.ericlam.mc.pvptitles.webapi.main;

import com.ericlam.mc.pvptitles.webapi.request.RequestHandler;
import io.javalin.Javalin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class PvPWebAPI extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Initialing PvPTitles Web API");
        this.saveDefaultConfig();
        this.reloadConfig();
        final int port = this.getConfig().getInt("web-port");
        boolean allEnabled = this.getConfig().getBoolean("cors-enable-all");
        List<String> allowed = this.getConfig().getStringList("cors-allowed-list");
        Bukkit.getScheduler().runTaskAsynchronously(this, ()->{
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(PvPWebAPI.class.getClassLoader());
            Javalin app = Javalin.create(config->{
                if (allEnabled){
                    config.enableCorsForAllOrigins();
                }else{
                    config.enableCorsForOrigin(allowed.toArray(new String[0]));
                }
                config.defaultContentType = "application/json";
            }).start(port);
            Thread.currentThread().setContextClassLoader(classLoader);
            RequestHandler.run(app, this.getConfig());
        });
    }
}
