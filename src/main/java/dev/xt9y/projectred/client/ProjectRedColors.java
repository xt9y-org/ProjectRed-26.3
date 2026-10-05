package dev.xt9y.projectred.client;

/**
 * Exact MrTJPCore/ProjectRed colour palette used by the legacy renderer.
 */
final class ProjectRedColors {
    private static final int[] RGB = {
            0xFFFFFF, // white
            0xC06300, // orange
            0xB51AB5, // magenta
            0x6F84F1, // light blue
            0xBFBF00, // yellow
            0x6BF100, // lime
            0xF14675, // pink
            0x535353, // grey
            0x939393, // light grey
            0x008787, // cyan
            0x5E00C0, // purple
            0x1313C0, // blue
            0x4F2700, // brown
            0x088700, // green
            0xA20F06, // red
            0x1F1F1F  // black
    };

    static int rgb(int index) {
        return RGB[index & 15];
    }

    private ProjectRedColors() {}
}
