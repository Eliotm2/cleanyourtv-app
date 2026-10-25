package tv.cleanyour.core.trust

import tv.cleanyour.core.model.LauncherArtifact
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.store.BlobStore

// Trust anchors compiled into the app. Changing one is a reviewed code change, never a data file.
object TrustRoots {
    // Root of the module index chain: signs keys.json only (KeysFile). Release key K2R, generated
    // 2026-10-20 for 0.1.0; it replaced the development keys of K2, which never signed a public
    // build. The private half stays offline with the maintainer (docs/history/DECISIONS-W5.md, K2R).
    const val INDEX_ROOT_PUBLIC_KEY = "RWTBm+H9rjMuaX944TBay7uDMxQEC8Q+E8yF1p7hyGl7Fa+8bzBKE4io"   // key id 692E33AEFDE19BC1

    // The sequence of the keys.json bundled with this build: an older keys file is refused.
    const val BUNDLED_KEYS_SEQUENCE = 1L

    val indexRoot: MinisignKey get() = Ed25519Minisign.parsePublicKey(INDEX_ROOT_PUBLIC_KEY)

    fun indexVerifier(): MinisignIndexVerifier = MinisignIndexVerifier(indexRoot, keysFloor = BUNDLED_KEYS_SEQUENCE)
}

// Fork (Thomas): the launcher bundled here is Thomas's fork of github.com/Gathin23/cleanyourtv
// (launcher 3.0 plus weather, night mode and stats; branch "thomas" of the fork, commit below),
// `assembleRelease` signed with the fork's own key (CN=Accueil TV, O=Thomas), the same key that signs
// this app and the curtain. The verifyLauncherPin task (part of :core:trust check and of
// :android:platform preBuild) fails the build when the asset differs.
object PinnedLauncher {
    const val ASSET_NAME = "cleanyourtv-launcher.apk"
    const val UPSTREAM_COMMIT = "fdd69b3"
    const val PACKAGE = "com.gathin.tvhome"
    const val VERSION_CODE = 13L
    const val VERSION_NAME = "3.0-thomas.4"
    const val SIZE = 537_229L
    const val SHA256 = "01829c5d0452a47e166cd74d4455a95781656cff71ef994537469363437e7d14"
    // SHA-256 of the signing certificate (DER), "CN=Accueil TV, O=Thomas, C=FR", v2 and v3.
    const val SIGNER_SHA256 = "fa73a3a033c6bb075706307ff446928530b90305a198b4349b02c4ce1eae29d5"
}

// The TV curtain bundled in the phone app (RFC-CURTAIN-1): module :tv:curtain of this repo, built
// twice and compared, then signed with the launcher release key by scripts/build-curtain-on-forge.sh.
// One signer to pin: SIGNER_SHA256 is the launcher's. The verifyCurtainPin task (part of :core:trust
// check and of :android:platform preBuild) fails the build when the asset differs.
object PinnedCurtain {
    const val ASSET_NAME = "cleanyourtv-curtain.apk"
    const val PACKAGE = "tv.cleanyour.curtain"
    const val ACTIVITY = ".CurtainActivity"
    const val RECEIVER = ".CueReceiver"
    const val VERSION_CODE = 1L
    const val VERSION_NAME = "0.1"
    const val SIZE = 61_859L
    const val SHA256 = "b4b7d6df2676bd85935d292694966369061ac945fe6597ee13d53fa2f5c95079"
    const val SIGNER_SHA256 = PinnedLauncher.SIGNER_SHA256
}

// LauncherPin over the BlobStore's bundled asset. bundled() refuses an asset whose bytes are not the
// pinned ones, so a swapped asset can never be offered as the launcher.
class BundledLauncherPin(private val blobs: BlobStore) : LauncherPin {
    override fun bundled(): LauncherArtifact {
        val blob = blobs.bundled(PinnedLauncher.ASSET_NAME)
        check(blob.sha256.hex == PinnedLauncher.SHA256 && blob.size == PinnedLauncher.SIZE) {
            "bundled launcher does not match its pin (sha256 ${blob.sha256.hex}, ${blob.size} bytes)"
        }
        return LauncherArtifact(
            pkg = PackageName(PinnedLauncher.PACKAGE),
            versionCode = PinnedLauncher.VERSION_CODE,
            versionName = PinnedLauncher.VERSION_NAME,
            sha256 = Sha256(PinnedLauncher.SHA256),
            signer = Sha256(PinnedLauncher.SIGNER_SHA256),
            blob = blob,
        )
    }
}

// CurtainPin over the BlobStore's bundled asset, refusing an asset whose bytes are not the pinned ones.
class BundledCurtainPin(private val blobs: BlobStore) : CurtainPin {
    override fun bundled(): LauncherArtifact {
        val blob = blobs.bundled(PinnedCurtain.ASSET_NAME)
        check(blob.sha256.hex == PinnedCurtain.SHA256 && blob.size == PinnedCurtain.SIZE) {
            "bundled curtain does not match its pin (sha256 ${blob.sha256.hex}, ${blob.size} bytes)"
        }
        return LauncherArtifact(
            pkg = PackageName(PinnedCurtain.PACKAGE),
            versionCode = PinnedCurtain.VERSION_CODE,
            versionName = PinnedCurtain.VERSION_NAME,
            sha256 = Sha256(PinnedCurtain.SHA256),
            signer = Sha256(PinnedCurtain.SIGNER_SHA256),
            blob = blob,
        )
    }
}
