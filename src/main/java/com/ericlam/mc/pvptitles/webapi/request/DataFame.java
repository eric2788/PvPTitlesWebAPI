package com.ericlam.mc.pvptitles.webapi.request;

import com.alternacraft.pvptitles.Listeners.HandlePlayerFame;
import com.alternacraft.pvptitles.Misc.PlayerFame;
import com.alternacraft.pvptitles.Misc.Rank;

import java.util.UUID;

public class DataFame implements Comparable<DataFame> {

    private UUID uuid;
    private int leader;
    private String name;
    private long seconds;
    private Rank rank;
    private Rank.NextRank nextRank;
    private int killStreaks;

    DataFame(PlayerFame fame, long seconds, Rank rank, Rank.NextRank nextRank) {
        this.uuid = UUID.fromString(fame.getUUID());
        this.leader = fame.getFame();
        this.name = fame.getName();
        this.seconds = seconds;
        this.rank = rank;
        this.nextRank = nextRank;
        this.killStreaks = HandlePlayerFame.getKillStreakFrom(fame.getUUID());
    }

    @Override
    public int compareTo(DataFame o) {
        return Integer.compare(this.leader, o.leader);
    }
}
