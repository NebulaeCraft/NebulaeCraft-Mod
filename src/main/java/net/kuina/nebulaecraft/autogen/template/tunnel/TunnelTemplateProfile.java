package net.kuina.nebulaecraft.autogen.template.tunnel;

/** Material palette and geometric limits owned by one tunnel template module. */
public final class TunnelTemplateProfile {
    public final int clearWidth;
    public final int clearHeight;
    public final int catenaryRecessHeight;
    public final double minimumRadius;
    public final double maximumGrade;
    public final String fullConcrete;
    public final String horizontalConcreteSlab;
    public final String verticalConcreteSlab;
    public final String centerTrackbed;
    public final String sideTrackbed;
    public final String reinforcedTrack;
    public final String platform;

    public TunnelTemplateProfile(int clearWidth, int clearHeight, int catenaryRecessHeight,
                                 double minimumRadius, double maximumGrade, String fullConcrete,
                                 String horizontalConcreteSlab, String verticalConcreteSlab,
                                 String centerTrackbed, String sideTrackbed,
                                 String reinforcedTrack, String platform) {
        if (clearWidth < 1 || (clearWidth & 1) == 0
                || clearHeight < 1 || catenaryRecessHeight < 0) {
            throw new IllegalArgumentException(
                    "Tunnel clear width must be positive and odd; heights must be valid");
        }
        if (minimumRadius <= 0.0 || maximumGrade <= 0.0) {
            throw new IllegalArgumentException("Tunnel route limits must be positive");
        }
        this.clearWidth = clearWidth;
        this.clearHeight = clearHeight;
        this.catenaryRecessHeight = catenaryRecessHeight;
        this.minimumRadius = minimumRadius;
        this.maximumGrade = maximumGrade;
        this.fullConcrete = fullConcrete;
        this.horizontalConcreteSlab = horizontalConcreteSlab;
        this.verticalConcreteSlab = verticalConcreteSlab;
        this.centerTrackbed = centerTrackbed;
        this.sideTrackbed = sideTrackbed;
        this.reinforcedTrack = reinforcedTrack;
        this.platform = platform;
    }
}
