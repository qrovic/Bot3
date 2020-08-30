package com.vegazsdev.bobobot.commands.def;

import com.vegazsdev.bobobot.TelegramBot;
import com.vegazsdev.bobobot.core.Command;
import com.vegazsdev.bobobot.db.PrefObj;
import org.telegram.telegrambots.meta.api.objects.Update;

public class ListAllCommands extends Command {

    public ListAllCommands() {
        super("cmds", "Show all available commands");
    }

    @Override
    public void botReply(Update update, TelegramBot bot, PrefObj prefs) {
        String hotkey = prefs.getHotkey();
        StringBuilder allCommandsAsString = new StringBuilder();
        for (int i = 0; i < bot.getActiveCommandsAsCmdObject().size(); i++) {
            allCommandsAsString.append("*").append(hotkey).append(bot.getActiveCommandsAsCmdObject().get(i).getAlias()).append("*\n")
                    .append("_").append(bot.getActiveCommandsAsCmdObject().get(i).getCommandInfo()).append("_\n\n");
        }
        bot.sendMessage(allCommandsAsString.toString(), update);
    }
}
