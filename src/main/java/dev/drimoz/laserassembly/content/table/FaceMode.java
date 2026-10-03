package dev.drimoz.laserassembly.content.table;

/** What a face of the table lets automation do. A click in the faces tab moves to the next. */
public enum FaceMode {
    NONE, INPUT, OUTPUT, BOTH;

    public FaceMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean takes() {
        return this == INPUT || this == BOTH;
    }

    public boolean gives() {
        return this == OUTPUT || this == BOTH;
    }
}
