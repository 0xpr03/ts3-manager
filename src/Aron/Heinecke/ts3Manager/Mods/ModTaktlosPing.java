package Aron.Heinecke.ts3Manager.Mods;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.API.Mod;
import Aron.Heinecke.ts3Manager.Lib.API.ModRegisters;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class ModTaktlosPing implements Mod {
    Logger logger = LogManager.getLogger();
    private static final String CLIENT_DB_ID = "client_database_id";
    private static final String CLIENT_DB_ID_SHORT = "cldbid";
    private static final String CLID = "clid";
    private static final String CID = "cid";
    private static final String CHANNEL_GUESTS = "9392";
    private static final String CLIENT_SERVER_GROUPS = "client_servergroups";
    private static final String GROUP_ID_NOTIFY = "13322";
    private static final String GROUP_ID_GUEST = "3083";
    private static final String MSG_PING = "Guest arrived!";
    private final long COOLDOWN_PING_TIME_MS = 1000 * 60 * 20;
    private final long COOLDOWN_CLIENT = 1000 * 60 * 30;
    private long LAST_PING = 0;
    private HashMap<String, Long> knownClients;
    private final Instance instance;
    private Timer timer;

    public ModTaktlosPing(Instance instance) {
        this.instance = instance;
        knownClients = new HashMap<>();
    }

    @java.lang.Override
    public ModRegisters registerEvents() {
        return new ModRegisters.Builder()
                .eventServer(true)
                .eventChannel(true)
                .build();
    }

    @java.lang.Override
    public void handleReady() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runPingCheck();
            }
        }, 1000, 5000);
    }

    private synchronized void runPingCheck() {
        try {
            Vector<HashMap<String, String>> res = instance.getTS3Connection().getConnector().getList(JTS3ServerQuery.LISTMODE_CLIENTLIST, "-groups");

            boolean newGuest = false;
            boolean hasLT = false;
            final List<String> ltsOnline = new ArrayList<>(10);
            for (HashMap<String, String> val : res) {
                final String cdid = val.get(CLIENT_DB_ID);
                final String cid = val.get(CID);
                final List<String> groups = Arrays.asList(val.get(CLIENT_SERVER_GROUPS).split(","));

                if (cid.equals(CHANNEL_GUESTS)) {
                    if (groups.contains(GROUP_ID_NOTIFY)) {
                        LAST_PING = System.currentTimeMillis();
                        hasLT = true;
                    } else if (groups.contains(GROUP_ID_GUEST)){
                        if (!knownClients.containsKey(cdid)) {
                            knownClients.put(cdid, System.currentTimeMillis());
                            newGuest = true;
                        } else if (System.currentTimeMillis() - knownClients.get(cdid) > COOLDOWN_CLIENT) {
                            knownClients.put(cdid, System.currentTimeMillis());
                            newGuest = true;
                        }
                    }
                } else if (groups.contains(GROUP_ID_NOTIFY)) {
                    ltsOnline.add(val.get(CLID));
                }
            }
            if (!hasLT && newGuest && System.currentTimeMillis() - LAST_PING > COOLDOWN_PING_TIME_MS) {
                LAST_PING = System.currentTimeMillis();
                notifyGroup(ltsOnline);
            }
            if (knownClients.size() > 0) {
                knownClients.values().removeIf(v -> v > COOLDOWN_CLIENT);
            }
        } catch (Exception e) {
            logger.error("Can't check for guests..", e);
        }
    }

    private void notifyGroup(List<String> ltsOnline) {
        ltsOnline.stream().forEach(c -> {
            try {
                instance.getTS3Connection().getConnector().pokeClient(Integer.valueOf(c), MSG_PING);
            } catch (TS3ServerQueryException e) {
                // ignore bots etc
                if (e.getErrorID() != 516 ) {
                    logger.error("can't poke", e);
                }
            }
        });
    }

    @Override
    public void handleConnectionLoss() {
        timer.cancel();
    }

    @Override
    public void handleReconnect() {
        handleReady();
    }

    @java.lang.Override
    public void handleShutdown() {
        timer.cancel();
    }

    @java.lang.Override
    public void handleClientJoined(HashMap<String, String> eventInfo) {

    }

    @java.lang.Override
    public void handleClientLeft(HashMap<String, String> eventInfo) {

    }

    @java.lang.Override
    public void handleTextMessage(String eventType, HashMap<String, String> eventInfo) {

    }

    @java.lang.Override
    public void handleClientMoved(HashMap<String, String> eventInfo) {

    }
}
