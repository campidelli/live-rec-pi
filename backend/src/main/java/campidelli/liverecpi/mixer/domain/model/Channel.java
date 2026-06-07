package campidelli.liverecpi.mixer.domain.model;

public class Channel {

    private final int index;
    private String name;
    private Color color;

    public Channel(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    public Color color() {
        return color;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void recolor(Color color) {
        this.color = color;
    }

    public record Color(
            String hexCode,
            boolean inverted) {
    }
}