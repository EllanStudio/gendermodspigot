package dbrighthd.wildfiregendermodplugin.wildfire.setup;

/** General fields present in the Beta.4 sync packet. */
public record GeneralOptions(GenderIdentities genderIdentity,
                             boolean hurtSounds,
                             float voicePitch) {
    public static class Builder {
        private GenderIdentities genderIdentity;
        private boolean hurtSounds;
        private float voicePitch;

        public Builder setGenderIdentity(GenderIdentities genderIdentity) {
            this.genderIdentity = genderIdentity;
            return this;
        }

        public Builder setHurtSounds(boolean hurtSounds) {
            this.hurtSounds = hurtSounds;
            return this;
        }

        public Builder setVoicePitch(float voicePitch) {
            this.voicePitch = voicePitch;
            return this;
        }

        public GeneralOptions create() {
            return new GeneralOptions(genderIdentity, hurtSounds, voicePitch);
        }
    }
}
