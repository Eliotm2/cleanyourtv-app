# Fork « thomas » de l'appli téléphone Clean Your TV

Branche `thomas` de [udhaykumarbala/cleanyourtv-app](https://github.com/udhaykumarbala/cleanyourtv-app) (0.3.7, MIT).
Identifiant d'appli `fr.thomas.cleanyourtv` (amont : `tv.cleanyour.app`), nom « Clean Your TV · Thomas », version
`0.3.7-thomas.1` : elle s'installe à côté de l'appli d'origine, qui garde son propre point de restauration.

## Ce que le fork change

- **Launcher embarqué** : le fork du launcher (`Gathin23/cleanyourtv`, branche `thomas`, 3.0-thomas.1, versionCode 10)
  à la place du launcher amont 2.7, signé avec la clé du fork, comme le rideau TV (`tv/curtain`, reconstruit et
  re-signé) et l'appli elle-même. Les épingles sont dans `core/trust/.../Pins.kt` ; `verifyLauncherPin` et
  `verifyCurtainPin` tournent à chaque compilation.
- **Installer une appli (APK)** : Manage › « Install an app ». Un fichier APK choisi sur le téléphone (sélecteur
  système, aucune permission) est copié dans le magasin de blobs, lu par le gestionnaire de paquets (paquet, version,
  clé), comparé à ce que la télé a, puis installé par le même chemin gardé et journalisé qu'un module
  (`PlanRequest.InstallApk`, `InstallApkMachine`, `InstallApkScreen`). « Undo everything » le retire comme toute
  appli installée par l'appli. Une copie installée avec une autre clé ou une version plus récente sur la télé est
  refusée avec la raison.
- **Remplacement du launcher à clé illisible** (0.3.7-thomas.2) : sur la TCL G08 la sonde n'a pas pu lire la clé du launcher
  amont déjà installé ; l'amont laissait alors la copie en place et l'installation échouait
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), bascule annulée. Le fork traite une clé illisible comme une autre clé : la copie
  est désinstallée d'abord, aux mêmes conditions (accueil d'origine aux commandes, règle D36). `SetupMachine` pose le
  consentement et `DefaultPlanner.launcherInstall` ajoute l'étape. En thomas.3, l'étape d'installation qui suit cette
  désinstallation ne déclare plus « absent avant » : la relecture du moteur au lancement voyait l'ancienne copie encore
  là, jugeait le plan périmé et l'appli rebouclait sur un nouveau plan.
- **Vérification de mise à jour** désactivée (`Store.updateChecker` répond « couldn't check ») : une version amont
  ne pourrait pas s'installer par-dessus cette clé.
- Chaînes du fork en anglais et en français (`feature/manage/res/values-fr`) ; le reste de l'appli reste en anglais.
- Tests : `LauncherPinTest` et `FullStoreTest` suivent le fork ; `PlannerTest.installApkFromThePhone` et quatre
  captures Roborazzi (`installapk_*`) ajoutés. Robolectric lit ses jars sur le miroir Google de Maven Central.

## Compiler

JDK 21, Gradle 9.7.1 (wrapper), `./gradlew :app:assembleFullRelease`. `keystore.properties` à la racine (jamais
commité : `storeFile`, `storePassword`, `keyAlias`, `keyPassword`) signe la release ; sans lui l'APK sort non signé.
Le sous-module `third_party/cleanyourtv` est requis (`git submodule update --init --depth 1`).

## Passer de l'appli d'origine au fork sur la télé

Le launcher déjà sur la télé est signé par l'amont ; Android refuse une mise à jour signée autrement, et l'appli ne
remplace le launcher que pendant que l'accueil d'origine de la télé est aux commandes (règle D36). Donc :

1. Dans l'appli **d'origine** : Manage › Home screen › « Use the TV's own home screen ». Le launcher amont reste
   installé, l'accueil Google TV revient.
2. Dans le fork « Clean Your TV · Thomas » : connecter la télé, lancer le nettoyage (« Make it clean »). Le plan
   affiche le remplacement du launcher (désinstallation de l'ancien, installation du nôtre), puis la bascule
   de l'accueil avec le chien de garde de 150 s.
3. Garder l'appli d'origine installée : son point de restauration est le seul qui précède le premier nettoyage.

Non vérifié faute de télé : le parcours ci-dessus sur la TCL, le sélecteur de fichiers sur ton téléphone, et
l'installation réelle d'un APK (le chemin est celui des modules, qui marche sur ta télé).
