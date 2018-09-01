package me.melijn.jda.commands.music;

import me.melijn.jda.Helpers;
import me.melijn.jda.blub.Category;
import me.melijn.jda.blub.Command;
import me.melijn.jda.blub.CommandEvent;
import me.melijn.jda.blub.Need;
import me.melijn.jda.music.MusicManager;
import me.melijn.jda.music.MusicPlayer;
import me.melijn.jda.utils.MessageHelper;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.Guild;

import java.util.regex.Pattern;

import static me.melijn.jda.Melijn.PREFIX;

public class NowPlayingCommand extends Command {

    public NowPlayingCommand() {
        this.commandName = "np";
        this.description = "Show you the song that the bot is playing";
        this.usage = PREFIX + this.commandName;
        this.aliases = new String[]{"nowplaying", "playing", "nplaying", "nowp"};
        this.category = Category.MUSIC;
        this.needs = new Need[]{Need.GUILD, Need.VOICECHANNEL};
        this.permissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    private MusicManager musicManager = MusicManager.getManagerInstance();
    public final static Pattern youtubePattern = Pattern.compile("^((?:https?:)?//)?((?:www|m)\\.)?((?:youtube\\.com))/watch(.*?)");
    public final static Pattern youtuBePattern = Pattern.compile("^((?:https?:)?//)?((?:www|m)\\.)?((?:youtu\\.be/))(.*?)");

    @Override
    protected void execute(CommandEvent event) {
        if (event.getGuild() != null) {
            if (Helpers.hasPerm(event.getGuild().getMember(event.getAuthor()), this.commandName, 0)) {
                Guild guild = event.getGuild();
                MusicPlayer audioPlayer = musicManager.getPlayer(guild);
                if (audioPlayer != null) {
                    AudioTrack track = audioPlayer.getAudioPlayer().getPlayingTrack();
                    String s = audioPlayer.getAudioPlayer().isPaused() ? "paused" : "playing";
                    if (track == null) event.reply("There are no songs playing at the moment");
                    else {
                        Emote emote = event.getGuild().getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_EXT_EMOJI) ?
                                event.getJDA().getEmoteById("445154561313865728") : null;
                        String loopedQueue = LoopQueueCommand.looped.getOrDefault(guild.getIdLong(), false) ? " :repeat:" : "";
                        String looped = LoopCommand.looped.getOrDefault(guild.getIdLong(), false) ? " :arrows_counterclockwise:" : "";
                        event.reply(new EmbedBuilder()
                                .setTitle("Now " + s)
                                .setColor(Helpers.EmbedColor)
                                .setThumbnail(MessageHelper.getThumbnailURL(track.getInfo().uri))
                                .setDescription("[**" + track.getInfo().title + "**](" + track.getInfo().uri + ")")
                                .addField("status:", (s.equalsIgnoreCase("playing") ? ":arrow_forward:" : ":pause_button:") + looped + loopedQueue, false)
                                .addField("progress:", MessageHelper.progressBar(track, emote), false)
                                .setFooter(Helpers.getFooterStamp(), Helpers.getFooterIcon()).build());
                    }
                } else {
                    event.reply("There are not songs playing");
                }
            } else {
                event.reply("You need the permission `" + commandName + "` to execute this command.");
            }
        } else {
            event.reply(Helpers.guildOnly);
        }
    }
}
