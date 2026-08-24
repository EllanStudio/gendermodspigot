package dbrighthd.wildfiregendermodplugin.wildfire.setup;

/** BreastPhysics fields present in the Beta.4 sync packet. */
public record PhysicsOptions(boolean breastPhysics,
                             boolean showInArmor,
                             float bounceMultiplier,
                             float floppiness) {
    public static class Builder {
        private boolean breastPhysics;
        private boolean showInArmor;
        private float bounceMultiplier;
        private float floppiness;

        public Builder setBreastPhysics(boolean breastPhysics) {
            this.breastPhysics = breastPhysics;
            return this;
        }

        public Builder setShowInArmor(boolean showInArmor) {
            this.showInArmor = showInArmor;
            return this;
        }

        public Builder setBounceMultiplier(float bounceMultiplier) {
            this.bounceMultiplier = bounceMultiplier;
            return this;
        }

        public Builder setFloppiness(float floppiness) {
            this.floppiness = floppiness;
            return this;
        }

        public PhysicsOptions create() {
            return new PhysicsOptions(breastPhysics, showInArmor, bounceMultiplier, floppiness);
        }
    }
}
