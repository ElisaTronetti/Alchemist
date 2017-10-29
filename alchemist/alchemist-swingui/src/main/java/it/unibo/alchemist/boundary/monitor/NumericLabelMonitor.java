package it.unibo.alchemist.boundary.monitor;

import it.unibo.alchemist.boundary.interfaces.OutputMonitor;
import it.unibo.alchemist.core.interfaces.Simulation;
import it.unibo.alchemist.model.interfaces.Concentration;
import it.unibo.alchemist.model.interfaces.Environment;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code OutputMonitor} that monitors the current {@link Simulation#getStep() steps} of the {@code Simulation}.
 *
 * @param <T> the {@link Concentration} type
 */
public abstract class NumericLabelMonitor<N, T> extends Label implements OutputMonitor<T> {
    private final N init;
    private volatile boolean mayRender = true;
    private volatile N current;
    private Optional<String> name;

    /**
     * Constructor.
     *
     * @param init the initial {@link N} class value
     */
    public NumericLabelMonitor(final @NotNull N init) {
        this.name = Optional.empty();
        this.init = Objects.requireNonNull(init);
        setTextFill(Color.WHITE);
        setText(init.toString());
    }

    /**
     * Constructor.
     *
     * @param init the initial {@link N} class value
     * @param name the name tag
     */
    public NumericLabelMonitor(final @NotNull N init, final @NotNull String name) {
        this(init);
        setName(name);
    }

    @Override
    public void initialized(final Environment<T> env) {
        update(init);
    }

    /**
     * Updates the GUI.
     *
     * @param val the value to update with
     */
    protected final void update(final N val) {
        current = val;
        if (mayRender) {
            mayRender = false;
            Platform.runLater(() -> {
                mayRender = true;
                setText(getName().isPresent() ? getName().get() + current.toString() : String.valueOf(current.toString()));
            });
        }
    }

    /**
     * Getter method for name tag to be put before the numerical value.
     *
     * @return the current name tag
     */
    protected Optional<String> getName() {
        return this.name;
    }

    /**
     * Setter method for name tag to be put before the numerical value.
     *
     * @param name the name tag
     */
    protected void setName(final @Nullable String name) {
        this.name = Optional.ofNullable(name);
    }

}
