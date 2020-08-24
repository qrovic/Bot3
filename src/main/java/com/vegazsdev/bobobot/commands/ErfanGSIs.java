package com.vegazsdev.bobobot.commands;

import com.vegazsdev.bobobot.TelegramBot;
import com.vegazsdev.bobobot.core.Command;
import com.vegazsdev.bobobot.db.PrefObj;
import com.vegazsdev.bobobot.utils.Config;
import com.vegazsdev.bobobot.utils.FileTools;
import com.vegazsdev.bobobot.utils.JSONs;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class ErfanGSIs extends Command {

    private static final Logger LOGGER = (Logger) LogManager.getLogger(ErfanGSIs.class);

    private static boolean isPorting = false;
    private static ArrayList<GSICmdObj> queue = new ArrayList<>();
    private final String toolPath = "ErfanGSIs/";
    private File[] supportedGSIs9 = new File(toolPath + "roms/9").listFiles(File::isDirectory);
    private File[] supportedGSIs10 = new File(toolPath + "roms/10").listFiles(File::isDirectory);
    private File[] supportedGSIs11 = new File(toolPath + "roms/11").listFiles(File::isDirectory);

    public ErfanGSIs() {
        super("jurl2gsi", "Can port gsi");
    }

    @Override
    public void botReply(Update update, TelegramBot bot, PrefObj prefs) {

        String msg = update.getMessage().getText();
        String idAsString = update.getMessage().getFrom().getId().toString();

        if (msg.contains(" allowuser") && Objects.equals(Config.getDefConfig("bot-master"), idAsString)) {
            if (update.getMessage().getReplyToMessage() != null) {
                String userid = update.getMessage().getReplyToMessage().getFrom().getId().toString();
                if (addPortPerm(userid)) {
                    bot.sendMessage(prefs.getString("egsi_allowed").replace("%1", userid), update);
                }
            } else if (msg.contains(" ")) {
                String userid = msg.split(" ")[2];
                if (userid != null && userid.trim().equals("") && addPortPerm(userid)) {
                    bot.sendMessage(prefs.getString("egsi_allowed").replace("%1", userid), update);
                }
            } else {
                bot.sendMessage(prefs.getString("egsi_allow_by_reply").replace("%1", prefs.getHotkey())
                        .replace("%2", this.getAlias()), update);
            }
        } else if (msg.contains(" queue")) {
            if (!queue.isEmpty()) {
                StringBuilder v = new StringBuilder();
                for (int i = 0; i < queue.size(); i++) {
                    v.append("#").append(i + 1).append(": ").append(queue.get(i).getGsi()).append("\n");
                }
                bot.sendMessage(prefs.getString("egsi_current_queue")
                        .replace("%2", v.toString())
                        .replace("%1", String.valueOf(queue.size())), update);
            } else {
                bot.sendMessage(prefs.getString("egsi_no_ports_queue"), update);
            }
        } else if (msg.contains(" cancel")) {

            // cancel by now, maybe work, not tested
            // will exit only on when porting is "active" (when url2gsi.sh is running)
            // after that when port already already finished (eg. uploading, zipping)
            // so this cancel needs more things to fully work

            ProcessBuilder pb;
            pb = new ProcessBuilder("/bin/bash", "-c", "kill -TERM -- -$(ps ax | grep url2GSI.sh | grep -v grep | awk '{print $1;}')");
            try {
                pb.start();
            } catch (IOException ex) {
                //Logger.getLogger(BotTelegram.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {

            boolean userHasPermissions = userHasPortPermissions(idAsString);

            if (userHasPermissions) {
                GSICmdObj gsiCommand = isCommandValid(update);
                if (gsiCommand != null) {
                    boolean isGSITypeValid = isGSIValid(gsiCommand.getGsi());
                    if (isGSITypeValid) {
                        if (!isPorting) {
                            isPorting = true;
                            createGSI(gsiCommand, bot);
                            while (queue.size() != 0) {
                                GSICmdObj portNow = queue.get(0);
                                queue.remove(0);
                                createGSI(portNow, bot);
                            }
                            isPorting = false;
                        } else {
                            queue.add(gsiCommand);
                            bot.sendMessage(prefs.getString("egsi_added_to_queue"), update);
                        }
                    } else {
                        bot.sendMessage(prefs.getString("egsi_supported_types")
                                .replace("%1",
                                        Arrays.toString(supportedGSIs9).replace(toolPath + "roms/9/", "")
                                                .replace("[", "")
                                                .replace("]", ""))
                                .replace("%2",
                                        Arrays.toString(supportedGSIs10).replace(toolPath + "roms/10/", "")
                                                .replace("[", "")
                                                .replace("]", ""))
                                .replace("%3",
                                        Arrays.toString(supportedGSIs11).replace(toolPath + "roms/11/", "")
                                                .replace("[", "")
                                                .replace("]", "")), update);

                    }
                }


            } else {
                // no perm
                bot.sendMessage("No Permissions", update);
            }

        }
    }


    private String try2AvoidCodeInjection(String parameters) {
        try {
            parameters = parameters.replace("&", "")
                    .replace("\\", "").replace(";", "").replace("<", "")
                    .replace(">", "");
        } catch (Exception e) {
            return parameters;
        }
        return parameters;
    }

    private GSICmdObj isCommandValid(Update update) {
        GSICmdObj gsiCmdObj = new GSICmdObj();
        String msg = update.getMessage().getText().replace(Config.getDefConfig("bot-hotkey") + this.getAlias() + " ", "");
        String url;
        String gsi;
        String param;
        try {
            url = msg.split(" ")[1];
            gsiCmdObj.setUrl(url);
            gsi = msg.split(" ")[2];
            gsiCmdObj.setGsi(gsi);
            param = msg.replace(url + " ", "").replace(gsi, "").trim();
            param = try2AvoidCodeInjection(param);
            gsiCmdObj.setParam(param);
            gsiCmdObj.setUpdate(update);
            return gsiCmdObj;
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            return null;
        }
    }

    private boolean isGSIValid(String gsi) {
        return true;
    }

    private boolean userHasPortPermissions(String idAsString) {
        if (Objects.equals(Config.getDefConfig("bot-master"), idAsString)) {
            return true;
        }
        String portConfigFile = "configs/allowed2port.json";
        return Arrays.asList(Objects.requireNonNull(JSONs.getArrayFromJSON(portConfigFile)).toArray()).contains(idAsString);
    }

    private void createGSI(GSICmdObj gsiCmdObj, TelegramBot bot) {
        Update update = gsiCmdObj.getUpdate();
        ProcessBuilder pb;
        pb = new ProcessBuilder("/bin/bash", "-c",
                "cd " + toolPath + " ; ./url2GSI.sh '" + gsiCmdObj.getUrl() + "' " + gsiCmdObj.getGsi() + " " + gsiCmdObj.getParam()
        );
        boolean success = false;
        StringBuilder fullLogs = new StringBuilder();
        fullLogs.append("Starting process!");
        int id = bot.sendMessage(fullLogs.toString(), update);
        try {
            pb.redirectErrorStream(true);
            Process process = pb.start();
            InputStream is = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            boolean weDontNeedAria2Logs = true;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                line = "`" + line + "`";
                if (line.contains("Downloading firmware to:")) {
                    weDontNeedAria2Logs = false;
                    fullLogs.append("\n").append(line);
                    bot.editMessage(fullLogs.toString(), update, id);
                }
                if (line.contains("Create Temp and out dir") || line.equals("Create Temp and out dir")) {
                    weDontNeedAria2Logs = true;
                }
                if (weDontNeedAria2Logs) {
                    fullLogs.append("\n").append(line);
                    bot.editMessage(fullLogs.toString(), update, id);
                    if (line.contains("GSI done on:")) {
                        success = true;
                    }
                }
            }

            if (success) {
                fullLogs.append("\n").append("Finished!");
                bot.editMessage(fullLogs.toString(), update, id);
                FileUtils.deleteDirectory(new File(toolPath + "output"));
            } else {
                throw new Exception("Task finished without generating a valid GSI");
            }
        } catch (Exception ex) {
            LOGGER.error(fullLogs);
        }
    }

    private boolean addPortPerm(String id) {
        try {
            if (new FileTools().checkFileExistsCurPath("configs/allowed2port.json")) {
                ArrayList z = JSONs.getArrayFromJSON("configs/allowed2port.json");
                if (z != null) {
                    z.add(id);
                }
                JSONs.writeArrayToJSON(z, "configs/allowed2port.json");
            } else {
                ArrayList<String> z = new ArrayList<>();
                z.add(id);
                JSONs.writeArrayToJSON(z, "configs/allowed2port.json");
            }
            return true;
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        }
    }

}

class GSICmdObj {

    private String url;
    private String gsi;
    private String param;
    private Update update;

    GSICmdObj() {
    }

    String getUrl() {
        return url;
    }

    void setUrl(String url) {
        this.url = url;
    }

    String getGsi() {
        return gsi;
    }

    void setGsi(String gsi) {
        this.gsi = gsi;
    }

    String getParam() {
        return param;
    }

    void setParam(String param) {
        this.param = param;
    }

    public Update getUpdate() {
        return update;
    }

    public void setUpdate(Update update) {
        this.update = update;
    }
}
