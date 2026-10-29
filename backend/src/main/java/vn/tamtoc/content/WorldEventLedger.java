package vn.tamtoc.content;
@FunctionalInterface public interface WorldEventLedger {boolean claim(String event,long cycle,long now);}
