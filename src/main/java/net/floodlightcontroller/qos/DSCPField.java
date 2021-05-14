package net.floodlightcontroller.qos;

public enum DSCPField {
    AF11((byte) 0b001010),
    AF12((byte) 0b001100),
    AF13((byte) 0b001110),
    AF21((byte) 0b010010),
    AF22((byte) 0b010100),
    AF23((byte) 0b010110),
    AF31((byte) 0b011010),
    AF32((byte) 0b011100),
    AF33((byte) 0b011110),
    AF41((byte) 0b100010),
    AF42((byte) 0b100100),
    AF43((byte) 0b100110),
    CS1((byte) 0b001000),
    CS2((byte) 0b010000),
    CS3((byte) 0b011000),
    CS4((byte) 0b100000),
    CS5((byte) 0b101000),
    CS6((byte) 0b110000),
    CS7((byte) 0b111000),
    Default((byte) 0b000000),
    EF((byte) 0b101110);

    private final byte dscpField;

    private DSCPField(Byte dscpField){
        this.dscpField = dscpField;
    }

    public byte getDSCPField() {
        return dscpField;
    }

}
