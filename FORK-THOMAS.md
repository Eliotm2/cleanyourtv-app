# Fork « thomas » de l'appli téléphone Clean Your TV

Branche `thomas` de [udhaykumarbala/cleanyourtv-app](https://github.com/udhaykumarbala/cleanyourtv-app) (0.4.7, MIT).
Identifiant d'appli `fr.thomas.cleanyourtv` (amont : `tv.cleanyour.app`), nom « Clean Your TV · Thomas ». Elle s'installe
à côté de l'appli d'origine, qui garde son propre point de restauration.

**Numéro** (depuis le 29/10/2026) : `<version du launcher amont embarqué>-thomas.N`, par exemple 3.2-thomas.1 = appli
avec notre launcher repris du launcher 3.2 de Gathin23. N avance à chaque nouvelle version (appli amont reprise, launcher
repris, ou changement à nous) et repart à 1 quand le launcher amont change. Les versions d'avant étaient numérotées sur
l'appli amont (0.4.6.1-thomas.1) ; 3.x passe devant, la mise à jour est donc proposée.

## Ce que le fork change

- **Launcher embarqué** : le fork du launcher (`Eliotm2/cleanyourtv`, branche `thomas`, 3.0-thomas.7, versionCode 16 :
  le launcher 3.0 amont b06ab21 plus nos ajouts) à la place du launcher amont épinglé, signé avec la clé du fork, comme le rideau TV (`tv/curtain`, reconstruit et
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
- **Switched off** (0.4.0-thomas.2) : au niveau Custom, les pastilles des groupes « ask » suivent l'état réel de la
  télé ; une appli coupée par un nettoyage antérieur (une autre appli) peut être remise.
- **Tests alignés sur `pm uninstall` sans `--user 0`** (EngineTest, golden undo-everything-remove-launcher).
- **Nom après la salutation** (0.4.6.1-thomas.1) : Manage › Home screen, champ « Name after the greeting ». Le nom est
  gardé sur le téléphone (`ForkGreeting`, fichier `fork-greeting-name.txt`) et ajouté à chaque look envoyé
  (`Look.encode` → `greeting_name`, que le launcher amont lit déjà : « BONSOIR, THOMAS »). Tant qu'aucun nom n'a été
  enregistré, les looks partent tels quels ; enregistré vide, la clé est omise.
- **Vérification de mise à jour, repli sur le flux** (3.2-thomas.2, d'après la 0.4.7 amont) : quand l'API GitHub refuse
  (60 requêtes par heure et par adresse, partagées sur un réseau mobile ou un VPN), l'appli lit le flux
  `releases.atom` du fork ; son dernier tag `-thomas.N` est la dernière version. La raison d'un échec va au journal.
- **Vérification de mise à jour** (0.4.0-thomas.1) : « Check for updates » lit les releases de ce fork
  (`Eliotm2/cleanyourtv-app`), signées avec la clé du fork, et ouvre la page de la dernière. Une version amont n'est
  jamais proposée : elle ne pourrait pas s'installer par-dessus cette clé. Ordre : la version amont, puis le numéro
  `-thomas.N` (`Store.forkNewer`), versions amont à quatre parties comprises (0.4.6.1).
- **Nombre d'apps par rangée** : depuis la 0.4.0 la feuille Look de l'amont choisit « apps in a row » (4 à 8) et
  l'écrit dans le look ; le réglage maison `ForkLook` (5 par défaut) est retiré. Choisir 5 une fois dans Look.
- **Workflows** : ceux de l'amont sont retirés (ils signent et publient avec les secrets de l'amont) ; seul
  `thomas-sync.yml` tourne.
- Chaînes du fork en anglais et en français (`feature/manage/res/values-fr`) ; le reste de l'appli reste en anglais.
- Tests : `LauncherPinTest` et `FullStoreTest` suivent le fork ; `PlannerTest.installApkFromThePhone` et quatre
  captures Roborazzi (`installapk_*`) ajoutés. Robolectric lit ses jars sur le miroir Google de Maven Central.

## À chaque reprise : vérifier ce que l'amont fait maintenant

À chaque reprise manuelle (et quand la reprise de nuit s'arrête), comparer les nouveautés de l'amont (CHANGELOG,
options du Look, nouvelles clés du launcher) avec nos ajouts : ce que l'amont fait désormais est retiré de notre côté
au profit du sien. Procédure et historique dans `FORK-THOMAS.md` du launcher (`Eliotm2/cleanyourtv`). Déjà retirés :
`ForkLook` (5 apps par rangée, remplacé par « apps in a row » de la 0.4.0). Essayé et gardé : `dock_bottom_room` du
launcher (« Every app in one row » de la 0.4.5 met les applis sur la ligne du dock au lieu de les garder en dessous).
On ne retire un ajout qu'après l'essai de Thomas sur la télé.

## Reprise automatique de l'original

`.github/workflows/thomas-sync.yml`, chaque nuit (01:37 UTC) et à la demande (Actions › « Run workflow ») :

1. `.github/thomas/sync.sh` fusionne la dernière release de l'appli amont dans `thomas` (workflows amont dehors ;
   épingle et APK du launcher : les nôtres), puis embarque la dernière release `launcher-*` du dépôt du launcher
   (`Eliotm2/cleanyourtv`, faite chaque nuit à 01:07 par son `thomas-launcher.yml`) : clé, paquet et version vérifiés,
   `pin-launcher.py` écrit l'APK, `Pins.kt` et `LauncherPinTest`. `.github/thomas/version.py` numérote l'appli
   `<launcher amont>-thomas.N` et règle le conflit de version.
2. Il s'arrête et ouvre une issue si un autre conflit reste.
3. Sinon : tests, APK signé avec la clé du fork (vérifiée), commit, push et release `<version>` avec l'APK.

Secrets du dépôt (Settings › Secrets and variables › Actions) : `KEYSTORE_B64` (le keystore en base64) et
`KEYSTORE_PROPERTIES` (`storePassword`, `keyAlias`, `keyPassword`). Le launcher (`Eliotm2/cleanyourtv`) n'a pas de
reprise nocturne : l'appli embarque le launcher que la release amont épingle, et c'est l'étape 2 qui signale un
changement.

## Publication à chaque version poussée

`.github/workflows/thomas-release.yml` : à chaque push sur `thomas` dont la version (`versionName`) n'a pas encore de
release, GitHub lance les tests, compile l'APK signé (clé et version vérifiées) et publie la release `<version>`. Les
versions faites à la main sont ainsi proposées par « Check for updates », comme celles de la reprise de nuit (qui
publie elle-même ; ses pushes, faits avec le jeton du workflow, ne relancent pas celui-ci).

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
