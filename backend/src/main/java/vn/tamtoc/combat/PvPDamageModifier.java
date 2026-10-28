package vn.tamtoc.combat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class PvPDamageModifier {
    private final double scale;
    public PvPDamageModifier(@Value("${game.duel.damage-modifier:1.0}")double scale){if(scale<=0||scale>3)throw new IllegalArgumentException("Invalid PvP damage modifier");this.scale=scale;}
    public int apply(int damage){return Math.max(1,(int)Math.floor(damage*scale));}
}
