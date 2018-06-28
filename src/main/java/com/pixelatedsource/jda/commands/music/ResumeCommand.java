package com.pixelatedsource.jda.commands.music;

import com.pixelatedsource.jda.Helpers;
import com.pixelatedsource.jda.blub.Category;
import com.pixelatedsource.jda.blub.Command;
import com.pixelatedsource.jda.blub.CommandEvent;
import com.pixelatedsource.jda.blub.Need;
import com.pixelatedsource.jda.music.MusicManager;
import com.pixelatedsource.jda.music.MusicPlayer;
import net.dv8tion.jda.core.entities.VoiceChannel;

import static com.pixelatedsource.jda.PixelSniper.PREFIX;

public class ResumeCommand extends Command {

    public ResumeCommand() {
        this.commandName = "resume";
        this.description = "Resume the paused song when paused";
        this.usage = PREFIX + this.commandName;
        this.aliases = new String[]{"unpause"};
        this.needs = new Need[] {Need.GUILD, Need.SAME_VOICECHANNEL_OR_DISCONNECTED};
        this.category = Category.MUSIC;
    }

    @Override
    protected void execute(CommandEvent event) {
        if (Helpers.hasPerm(event.getGuild().getMember(event.getAuthor()), this.commandName, 0)) {
            MusicPlayer player = MusicManager.getManagerinstance().getPlayer(event.getGuild());
            VoiceChannel voiceChannel = event.getGuild().getMember(event.getAuthor()).getVoiceState().getChannel();
            if (voiceChannel == null) voiceChannel = event.getGuild().getSelfMember().getVoiceState().getChannel();
            if (voiceChannel != null) {
                event.getGuild().getAudioManager().openAudioConnection(voiceChannel);
                player.resumeTrack();
                if (player.getAudioPlayer().getPlayingTrack() == null && player.getListener().getTrackSize() > 0)
                    player.skipTrack();
                event.reply("Resumed by **" + event.getAuthor().getName() + "#" + event.getAuthor().getDiscriminator() + "**");
            } else {
                event.reply("You or me have to be in a voice channel to resume");
            }
        } else {
            event.reply("You need the permission `" + commandName + "` to execute this command.");
        }
    }
}
