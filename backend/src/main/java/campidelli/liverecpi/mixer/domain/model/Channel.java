package campidelli.liverecpi.mixer.domain.model;

import java.util.Optional;

public class Channel {

    private final int index;
    private Optional<String> name = Optional.empty();
    private Optional<Color> color = Optional.empty();

    public Channel(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public Optional<String> name() {
        return name;
    }

    public Optional<Color> color() {
        return color;
    }

    public void rename(String name) {
        this.name = Optional.ofNullable(name);
    }

    public void recolor(Color color) {
        this.color = Optional.ofNullable(color);
    }

    public record Color(
            String hexCode,
            boolean inverted) {
    }
}