package plugin.commands;

import arc.Events;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Strings;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import plugin.commands.annotations.ChatCommand;
import plugin.etc.Ranks;
import plugin.models.PlayerData;
import plugin.models.PlayerDataCollection;
import useful.Bundle;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static arc.util.Strings.canParseInt;
import static plugin.ConfigJson.discordUrl;
import static plugin.Plugin.players;
import static plugin.Plugin.servers;
import static plugin.commands.Menus.achMenu;
import static plugin.commands.history.History.historyPlayers;
import static plugin.functions.Other.statsMenu;
import static plugin.utils.Utilities.*;

@SuppressWarnings("unused")
public class ChatCommands {
    public static AtomicInteger votes = new AtomicInteger(0);
    public static Seq<Player> votedPlayer = new Seq<>();
    public static boolean isVoting = false;

    @ChatCommand(name = "announce", args = "<str text>", description = "calls an announce", requiredRank = Ranks.Rank.Moderator, minArgsCount = 1, isLastArgText = true)
    public void announce(Player player, List<String> args) {
        Call.announce(args.get(0));
    }

    @ChatCommand(name = "gameover", description = "Executes a gameover event", requiredRank = Ranks.Rank.Moderator)
    public void gameover(Player player, List<String> args) {
        Events.fire(new EventType.GameOverEvent(Team.derelict));
    }

    @ChatCommand(name = "players", description = "Lists all players on the server")
    public void players(Player player, List<String> args) {
        StringBuilder list = new StringBuilder();
        for (Player plr : Groups.player) {
            PlayerData data = new PlayerData(plr);
            if (data.isExist()) {
                list.append(plr.name()).append("; [white]ID: ").append(data.getId()).append("\n");
            }
        }
        player.sendMessage(String.valueOf(list));
    }

    @ChatCommand(name = "js", args = "<str code>", description = "Execute JS code", requiredRank = Ranks.Rank.JS, minArgsCount = 1, isLastArgText = true)
    public void javascript(Player player, List<String> args) {
        runJs(args.get(0), resp -> {
            if (!resp.isEmpty()) player.sendMessage(resp);
        });
    }

    @ChatCommand(name = "maps", args = "[int page]", description = "List all maps", maxArgsCount = 1)
    public void maps(Player player, List<String> args) {
        StringBuilder list = new StringBuilder();
        int page;
        if (args.isEmpty()) {
            page = 0;
        } else {
            if (!canParseInt(args.get(0))) {
                player.sendMessage("[red]Page must be number!");
                return;
            }
            page = Integer.parseInt(args.get(0));
        }
        int mapsPerPage = 10;
        Seq<Map> maps = getMaps();
        maps.list().stream().skip(page * 10L).limit(mapsPerPage + (page * 10L)).forEach(
                map -> list.append(map.name()).append("[white], by ").append(map.author()).append("\n")
        );
        if (!String.valueOf(list).contains("by")) {
            player.sendMessage("[red]No maps detected!");
            return;
        }
        player.sendMessage(String.valueOf(list));
    }

    @ChatCommand(name = "rtv", args = "[str map_name]", description = "Rock the vote to change map!", maxArgsCount = 1)
    public void rtv(Player player, List<String> args) {
        final int[] votesRequired = new int[1];
        AtomicInteger time = new AtomicInteger(60);
        votesRequired[0] = (int) Math.ceil((double) Groups.player.size() / 2);
        Timer timer = new Timer();
        if (isVoting) {
            player.sendMessage("Vote is already running!");
            return;
        }
        Map choosedMap = Vars.maps.customMaps().find(map -> Strings.stripColors(map.name()).contains(args.get(0)));
        if (choosedMap == null) {
            player.sendMessage("Could not find that map!");
            return;
        }
        Call.sendMessage(player.name() + "[#e7e7e7] начал голосование за карту " + choosedMap.plainName() + "[#e7e7e7]. " + votes.get() + "голосов из " + votesRequired[0] + ". y или n чтобы проголосовать");
        isVoting = true;
        timer.schedule((new TimerTask() {
            @Override
            public void run() {
                time.getAndAdd(-1);
                votesRequired[0] = (int) Math.ceil((double) Groups.player.size() / 2);
                if (votes.get() >= votesRequired[0]) {
                    voteSuccess(choosedMap);
                    isVoting = false;
                    timer.cancel();
                }
                if (time.get() < 0) {
                    voteCanceled();
                    isVoting = false;
                    timer.cancel();
                }
            }
        }), 0, 1000);
    }

    @ChatCommand(name = "discord", description = "Link to our discord!")
    public void discord(Player player, List<String> args) {
        Call.openURI(player.con, discordUrl);
    }

    @ChatCommand(name = "stats", args = "[str name]", description = "Get stats of player or yourself", maxArgsCount = 1)
    public void stats(Player player, List<String> args) {
        if (args.isEmpty()) {
            statsMenu(player, player);
            return;
        }
        Player plr = findPlayerByName(args.get(0));
        if (plr == null) {
            player.sendMessage("Could not find that player!");
            return;
        }
        statsMenu(player, plr);
    }

    @ChatCommand(name = "history", description = "Enables/Disables history mode")
    public void history(Player player, ArrayList<String> args) {
        if (historyPlayers.contains(player.uuid())) {
            historyPlayers.remove(player.uuid());
            player.sendMessage("[red]History has been disabled!");
            Call.hideHudText(player.con());
            return;
        }
        historyPlayers.add(player.uuid());
        player.sendMessage("[green]History has been enabled!");
    }

    @ChatCommand(name = "joinmessage", args = "<str message>", description = "Makes custom join message! @ -> your name. Make sure this message wont break any rule!", minArgsCount = 1)
    public void joinMessage(Player player, ArrayList<String> args) {
        PlayerData data = new PlayerData(player);
        if (args.get(0).length() >= 45) {
            player.sendMessage("Too much symbols! Limit is 45");
        } else {
            data.setJoinMessage(args.get(0));
            player.sendMessage("[green]Changed your join message!");
        }
    }

    @ChatCommand(name = "leaderboard", description = "Shows leaderboard")
    public void leaderboard(Player player, ArrayList<String> args) {
        StringBuilder list = new StringBuilder();
        list.append("[orange]Playtime leaderboard: \n");
        FindIterable<PlayerDataCollection> sort = players.find().sort(new BasicDBObject("playtime", -1)).limit(10);
        for (PlayerDataCollection data : sort) {
            int playtime = data.playtime;
            list.append(data.rawName).append("[white]: ").append(Bundle.formatDuration(Duration.ofMinutes(playtime))).append("\n");
        }
        player.sendMessage(list.toString());
    }

    @ChatCommand(name = "achievements", description = "Views your achievements")
    public void achievements(Player player, ArrayList<String> args){
        achMenu(player);
    }

    @ChatCommand(name = "serverhop", args = "<str server_name>", description = "Hops to server", minArgsCount = 1)
    public void serverHop(Player player, ArrayList<String> args){
        JSONArray array = (JSONArray) servers.get("servers");
        Seq<Server> servers = new Seq<>();
        for (Object object : array) {
            JSONObject jsonObject = (JSONObject) object;
            Server serv = new Server((String) jsonObject.get("servername"), (String) jsonObject.get("ip"), (Long) jsonObject.get("port"));
            servers.add(serv);
        }
        if (servers.contains(server -> server.name.contains(args.get(0)))) {
            Server serv = servers.find(server -> server.name.contains(args.get(0)));
            Call.connect(player.con, serv.ip, Math.toIntExact(serv.port));
        }
    }

    @ChatCommand(name = "servers", description = "Lists all servers")
    public void servers(Player player, ArrayList<String> args){
        JSONArray array = (JSONArray) servers.get("servers");
        StringBuilder list = new StringBuilder();
        list.append("[yellow]SERVER LIST:\n\n[white]");
        for (Object object : array) {
            JSONObject jsonObject = (JSONObject) object;
            list.append(jsonObject.get("servername")).append(": ").append(jsonObject.get("ip")).append(":").append(jsonObject.get("port")).append("\n");
        }
        player.sendMessage(list.toString());
    }
}
