package campidelli.liverecpi;

import org.jspecify.annotations.NonNull;

import io.micronaut.runtime.Micronaut;

public class Application {

    public static void main(String @NonNull [] args) {
        Micronaut.run(Application.class, args);
    }
}
