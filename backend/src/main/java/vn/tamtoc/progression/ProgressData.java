package vn.tamtoc.progression;

import java.util.*;

/** Durable per-character aggregate. Client requests never bind to this type. */
public class ProgressData {
    public Set<String> discoveredMaps=new HashSet<>(),discoveredMonsters=new HashSet<>();
    public Map<String,Integer> questObjectives=new HashMap<>();
    public Map<String,Long> nextDungeonAt=new HashMap<>();
    public java.util.Map<String,String> checkpoints=new java.util.HashMap<>();
    public java.util.Map<String,Integer> mapVersions=new java.util.HashMap<>();
    public java.util.Map<String,Integer> killsByDefinition=new java.util.HashMap<>();
    public java.util.Map<String,Integer> questKillStarts=new java.util.HashMap<>();
    public int hp=100, mp=60, stones=0;
    public int wolfKills=0, totalKills=0;
    public Map<String,Integer> inventory=new LinkedHashMap<>(Map.of("WOOD_SWORD",1,"ROBE",1,"POTION",3));
    public Map<String,String> equipment=new LinkedHashMap<>(Map.of("WEAPON","WOOD_SWORD","ARMOR","ROBE"));
    public Map<String,String> quests=new LinkedHashMap<>();
    public int questWolfStart, trialKillStart;
    public String trialOrganization;
    public boolean guideMet;
    public Map<String,Long> cooldowns=new HashMap<>();
    public long respawnAt;
}
