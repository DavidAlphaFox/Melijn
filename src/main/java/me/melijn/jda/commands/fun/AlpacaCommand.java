package me.melijn.jda.commands.fun;

import me.melijn.jda.Helpers;
import me.melijn.jda.blub.Category;
import me.melijn.jda.blub.Command;
import me.melijn.jda.blub.CommandEvent;
import me.melijn.jda.utils.MessageHelper;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;

import static me.melijn.jda.Melijn.PREFIX;

public class AlpacaCommand extends Command {

    public AlpacaCommand() {
        this.commandName = "alpaca";
        this.description = "shows a sexy alpaca";
        this.usage = PREFIX + commandName;
        this.category = Category.FUN;
    }

    @Override
    protected void execute(CommandEvent event) {
        if (event.getGuild() == null || Helpers.hasPerm(event.getMember(), this.commandName, 0)) {
            if (event.getGuild() == null || event.getGuild().getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_EMBED_LINKS))
                event.reply(new EmbedBuilder()
                        .setColor(Helpers.EmbedColor)
                        .setDescription("Enjoy your alpaca!")
                        .setImage("http://randomalpaca.com/wp-content/uploads/2015/04/alpaca" + MessageHelper.randInt(1, 144) + ".jpg")
                        .build());
            else
                event.reply("Enjoy your alpaca\n"
                        + "http://randomalpaca.com/wp-content/uploads/2015/04/alpaca" + MessageHelper.randInt(1, 144) + ".jpg");
        } else {
            event.reply("You need the permission `" + commandName + "` to execute this command.");
        }
    }
}