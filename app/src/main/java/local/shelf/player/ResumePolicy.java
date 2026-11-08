/* Java port of Kinema src/player/resume.ts, revision 9b61dc4cb28cc018c12eae13bdc712dace67ccd4.
 * Copyright (c) 2026 Basswaves, MIT. See assets/licenses/Kinema-MIT.txt.
 * Changes: milliseconds; local-only progress (no Trakt fraction). */
package local.shelf.player;
final class ResumePolicy {
    static long resume(long position,long duration,boolean completed){
        if(completed && !(duration>0 && (double)position/duration<.8))return 0;
        if(position<30_000)return 0;
        if(duration>0 && (double)position/duration>=.94)return 0;
        return position;
    }
    static boolean complete(long position,long duration){return duration>0 && (double)position/duration>=.94;}
}
