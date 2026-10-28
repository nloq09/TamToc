package vn.tamtoc.realtime;

public class ProtocolException extends IllegalArgumentException {
    private final String code;
    public ProtocolException(String code,String message){super(message);this.code=code;}
    public String code(){return code;}
}
