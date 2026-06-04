package campidelli.liverecpi.domain.mixer;

public record ChannelColor(
    Color value,
    boolean inverted) {

  public enum Color {
    OFF,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    MAGENTA,
    CYAN,
    WHITE
  }
}