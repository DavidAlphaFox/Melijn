package com.pixelatedsource.jda.commands.music;

import com.pixelatedsource.jda.Helpers;
import com.pixelatedsource.jda.blub.Category;
import com.pixelatedsource.jda.blub.Command;
import com.pixelatedsource.jda.blub.CommandEvent;
import com.pixelatedsource.jda.blub.Need;
import com.pixelatedsource.jda.music.MusicManager;
import com.pixelatedsource.jda.music.MusicPlayer;
import com.pixelatedsource.jda.utils.MessageHelper;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;

import java.util.concurrent.BlockingQueue;

import static com.pixelatedsource.jda.PixelSniper.PREFIX;

public class SkipCommand extends Command {

    public SkipCommand() {
        this.commandName = "skip";
        this.description = "Skip to a song in the queue";
        this.usage = PREFIX + this.commandName + " [1-50]";
        this.category = Category.MUSIC;
        this.needs = new Need[]{Need.GUILD, Need.SAME_VOICECHANNEL};
        this.permissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    private MusicManager manager = MusicManager.getManagerinstance();

    @Override
    protected void execute(CommandEvent event) {
        if (Helpers.hasPerm(event.getGuild().getMember(event.getAuthor()), this.commandName, 0)) {
            MusicPlayer player = manager.getPlayer(event.getGuild());
            AudioTrack shippableTrack = player.getAudioPlayer().getPlayingTrack();
            if (shippableTrack == null) {
                event.reply("There are no songs playing at the moment");
                return;
            }
            String[] args = event.getArgs().split("\\s+");
            BlockingQueue<AudioTrack> audioTracks = player.getListener().getTracks();
            int i = 1;
            if (args.length > 0) {
                if (!args[0].equalsIgnoreCase("")) {
                    if (args[0].matches("\\d+") && args[0].length() < 4) {
                        i = Integer.parseInt(args[0]);
                        if (i >= 50 || i < 1) {
                            MessageHelper.sendUsage(this, event);
                            return;
                        }
                    } else {
                        MessageHelper.sendUsage(this, event);
                        return;
                    }
                }
            }
            AudioTrack nextSong = null;
            int c = 0;
            for (AudioTrack track : audioTracks) {
                if (i != c) {
                    nextSong = track;
                    player.skipTrack();
                    c++;
                }
            }
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("Skipped");
            eb.setColor(Helpers.EmbedColor);
            String songOrSongs = i == 1 ? "song" : "songs";
            if (nextSong != null)
                eb.setDescription("Skipped " + i + " " + songOrSongs + "\nPrevious song: **[" + shippableTrack.getInfo().title + "](" + shippableTrack.getInfo().uri + ")**\n" + "Now playing: **[" + nextSong.getInfo().title + "](" + nextSong.getInfo().uri + ")** " + Helpers.getDurationBreakdown(nextSong.getInfo().length));
            else {
                player.skipTrack();
                eb.setDescription("Skipped " + i + " " + songOrSongs + "\nPrevious song: `" + shippableTrack.getInfo().title + "`\n" + "No next song to play");
            }
            eb.setFooter(Helpers.getFooterStamp(), Helpers.getFooterIcon());
            event.reply(eb.build());
        } else {
            event.reply("You need the permission `" + commandName + "` to execute this command.");
        }
    }
}
