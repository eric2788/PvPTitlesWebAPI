package com.ericlam.mc.pvptitles.webapi.request;

import com.alternacraft.pvptitles.Exceptions.CustomException;
import com.alternacraft.pvptitles.Exceptions.DBException;
import com.alternacraft.pvptitles.Main.CustomLogger;
import com.alternacraft.pvptitles.Main.Manager;
import com.alternacraft.pvptitles.Main.PvpTitles;
import com.alternacraft.pvptitles.Managers.RankManager;
import com.alternacraft.pvptitles.Managers.TimerManager;
import com.alternacraft.pvptitles.Misc.PlayerFame;
import com.alternacraft.pvptitles.Misc.Rank;
import com.alternacraft.pvptitles.Misc.TimedPlayer;
import com.ericlam.mc.pvptitles.webapi.main.PvPWebAPI;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.core.validation.JavalinValidation;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import javax.annotation.Nonnull;
import java.security.SecureRandom;
import java.util.*;

public class RequestHandler {
    private final Javalin app;
    private final FileConfiguration config;
    private List<PlayerFame> fameArray;
    private final Manager manager;
    private final Gson gson;
    private final PvPWebAPI pvPWebAPI;
    private String token;

    private RequestHandler(Javalin app, FileConfiguration config){
        this.pvPWebAPI = PvPWebAPI.getPlugin(PvPWebAPI.class);
        this.app = app;
        this.config = config;
        this.manager = PvpTitles.getInstance().getManager();
        this.gson = new Gson();
        this.token = Optional.ofNullable(config.getString("token")).orElseGet(this::generateToken);
        refresh();

        JavalinValidation.register(Short.class, v -> {
            try {
                return Short.parseShort(v);
            } catch (NumberFormatException e) {
                throw new BadRequestResponse(e.getMessage());
            }
        });

        JavalinValidation.register(Boolean.class, Boolean::parseBoolean);

        JavalinValidation.register(UUID.class, v->{
            try{
                return UUID.fromString(v);
            }catch (Exception e){
                throw new BadRequestResponse("Invalid UUID Format");
            }
        });

    }

    public static void run(Javalin app, FileConfiguration config) {
        new RequestHandler(app, config).launch();
    }

    private String generateToken() {
        String token = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(36));
        config.set("token", token);
        pvPWebAPI.saveConfig();
        this.token = token;
        return token;
    }

    private void launch() {
        app.exception(Exception.class, (e, context) -> {
            context.result(returnError(e, context.queryParam("debug", Boolean.class, "false").get()));
        });
        app.get("/list", ctx -> {
            short l = ctx.queryParam("limit", Short.class).get();
            short start = ctx.queryParam("start", Short.class, "0").get();
            List<PlayerFame> fames = fameArray.subList(start, Math.min(l, fameArray.size()));
            Set<DataFame> dataFames = getAllDatas(fames);
            ctx.result(gson.toJson(dataFames));
        });
        app.post("/refresh", ctx -> {
            Map token = gson.fromJson(ctx.body(), Map.class);
            if (token == null || token.isEmpty()){
                throw new UnauthorizedResponse("Missing token");
            }
            if (!token.get("token").equals(this.token)) {
                throw new UnauthorizedResponse("The token is invalid");
            }
            Map<String, Object> map = new HashMap<>();
            map.put("success", refresh());
            map.put("next-token", generateToken());
            ctx.result(gson.toJson(map));
        });
        app.get("/info/:uuid", ctx ->{
            UUID uuid = ctx.pathParam("uuid", UUID.class).get();
            int fame = manager.getDBH().getDM().loadPlayerFame(uuid, null);
            long seconds = 0;
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            if (player == null || player.getName() == null){
                throw new NotFoundResponse("No this player");
            }
            try {
                seconds = manager.getDBH().getDM().loadPlayedTime(uuid)
                        + getTimedPlayer(player).getTotalOnline();
            } catch (DBException ex) {
                CustomLogger.logArrayError(ex.getCustomStackTrace());
            }
            Rank rank = RankManager.getRank(fame, seconds, player);
            Rank.NextRank nextRank = RankManager.getNextRank(rank, fame, seconds, player);
            ctx.result(gson.toJson(new DataFame(new PlayerFame(uuid.toString(), fame, seconds), seconds, rank, nextRank)));
        });
    }

    private String returnError(Exception e, boolean debug) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Error", "BadRequest");
        map.put("ErrorCode", 400);
        Map<String, Object> exceptionMap = new LinkedHashMap<>();
        exceptionMap.put("name", e.getClass().getSimpleName());
        exceptionMap.put("reason", e.getMessage());
        if (debug) exceptionMap.put("stacktrace", e.getStackTrace());
        map.put("Exception", exceptionMap);
        return gson.toJson(map);
    }

    private boolean refresh() {
        try {
            int limit = config.getInt("max-limit");
            fameArray = manager.getDBH().getDM().getTopPlayers((short)limit, "");
            return true;
        } catch (DBException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Nonnull
    private TimedPlayer getTimedPlayer(OfflinePlayer player){
        TimerManager timerManager = Manager.getInstance().getTimerManager();
        if (!timerManager.hasPlayer(player)){
            TimedPlayer tplayer = new TimedPlayer(PvpTitles.getInstance(), player);
            timerManager.addPlayer(tplayer);
        }
        return timerManager.getPlayer(player);
    }

    private Set<DataFame> getAllDatas(List<PlayerFame> fames) {
        Set<DataFame> datas = new TreeSet<>();
        for (PlayerFame fame : fames) {
            try {
                int fameInt = fame.getFame();
                OfflinePlayer player = fame.getPlayer();
                long actual = Manager.getInstance().getDBH().getDM().loadPlayedTime(UUID.fromString(fame.getUUID()));
                long session = getTimedPlayer(player).getTotalOnline();
                long seconds = actual + session;
                Rank rank = RankManager.getRank(fameInt, seconds, player);
                Rank.NextRank nextRank = RankManager.getNextRank(rank, fameInt, seconds, player);
                datas.add(new DataFame(fame, seconds, rank, nextRank));
            } catch (CustomException e) {
                pvPWebAPI.getLogger().warning(e.getMessage());
                CustomLogger.logArrayError(e.getCustomStackTrace());
            }
        }
        return datas;
    }


}
