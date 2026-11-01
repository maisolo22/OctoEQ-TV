# OctoEQ TV 0.3.0 — Mi Box : fidélité et compatibilité

Application Android TV indépendante, Android 7/API 24 minimum. Le mode fidèle exige Android 9/API 28 et un effet DynamicsProcessing accepté par le firmware. La compatibilité sonore sur Mi Box réelle reste à vérifier : une API acceptant les réglages ne prouve pas que les autres applications sont effectivement traitées.

## Ce qui change

- Mode **fidèle**, activé par défaut : essaie `DynamicsProcessing` avec un seul pré-EQ de dix bandes, entrée fixe à 0 dB, sans MBC, sans post-EQ ni limiteur configurés. Aucune normalisation suivant la bande la plus forte ; aucun changement du volume système.
- Chaque mouvement écrit seulement les coefficients modifiés. Le service est l'unique propriétaire des effets : plus de doubles écritures depuis l'écran, ni de réactivation du moteur à chaque mouvement.
- Le BassBoost et le Virtualizer matériels sont suspendus en mode fidèle, même si DynamicsProcessing est refusé. Ils peuvent eux-mêmes influer sur la dynamique. Pour des basses en mode fidèle, régler les bandes graves ou le profil Basses +.
- Si DynamicsProcessing est refusé : repli explicite vers `Equalizer`. Le diagnostic affiche la raison et avertit qu'une compensation de volume constructeur reste possible. Le mode **compatibilité** force cet ancien moteur et restitue l'accès aux effets matériels.
- Les réglages `band_N` de la version 0.2 restent conservés pour le moteur historique. Le nouveau moteur emploie `linear_band_N`, initialement plats : pas de transfert par indice entre cinq bandes matérielles et dix bandes logicielles. Chaque moteur retrouve ses réglages.
- Deux portées exclusives : globale expérimentale OU sessions de lecteurs compatibles. Jamais les deux à la fois, pour éviter un double EQ.

## Pourquoi augmenter une bande peut faire baisser le reste

Le code de la version 0.2.0 n'appelait pas le volume système et n'ajoutait pas de normalisation. En revanche, il utilisait `Equalizer`, `BassBoost` et `Virtualizer` du firmware, réécrivait toutes les bandes et réassignait l'activation à chaque changement. Le moteur AOSP LVM contient notamment une correction de gain général liée aux gains d'EQ : c'est une explication plausible de la baisse entendue, **pas un diagnostic mesuré de la Mi Box**. Le lecteur, la TV ou l'amplificateur peuvent aussi appliquer un traitement dynamique.

Le nouveau mode vise à éviter cette compensation dans la chaîne configurée par OctoEQ. Aucun contrôle n'est promis sur les traitements ajoutés ailleurs par Xiaomi, le lecteur, la TV ou l'amplificateur. Une bande couvre une plage de fréquences avec une transition ; elle ne modifie pas une unique fréquence sans aucun effet voisin. Sans réduction globale ni limiteur, un boost sur un flux déjà proche de 0 dBFS peut saturer. Commencer par +1 à +3 dB ; plafond du mode fidèle : +6 dB, minimum : −12 dB.

## Toutes les applications de la box ?

**Pas de garantie avec un APK ordinaire sans privilèges système.**

- **Portée globale (essai)** : attachement à la session 0. Android déprécie cet usage pour les effets d'insertion ; le firmware peut refuser l'effet ou certains chemins peuvent le contourner.
- **Lecteurs compatibles** : le service déjà actif écoute les annonces `OPEN_AUDIO_EFFECT_CONTROL_SESSION` / `CLOSE_AUDIO_EFFECT_CONTROL_SESSION`. Relancer la lecture après avoir choisi cette portée. Seuls les lecteurs qui annoncent leur session sont traitables. Aucun scan caché ni accès arbitraire à toutes les sessions. La liste en mémoire est bornée à 16 sessions, les annonces dupliquées sont ignorées, les effets sont libérés à la fermeture de la session.
- Certains lecteurs n'annoncent rien. Le passthrough/bitstream HDMI et certains chemins offload peuvent contourner le traitement. Essayer une sortie PCM/stéréo. Le mode fidèle est configuré pour la stéréo, pas certifié multicanal.
- Un traitement réellement maîtrisé requiert le DSP intégré au lecteur (par exemple une intégration explicite dans octoStream), ou un traitement système privilégié / externe sur le trajet audio. Root et intégration octoStream ne sont **pas implémentés**. Root ne garantit pas à lui seul le traitement des flux passthrough.

Le compteur de sessions et le contrôle acquis sont des diagnostics de connexion, pas des preuves acoustiques.

## Essai conseillé sur Mi Box

1. Choisir **Moteur : fidèle** puis **Portée : globale (essai)**. Lire le diagnostic : vérifier que DynamicsProcessing est effectivement sélectionné. Si le repli Equalizer apparaît, la correction de volume peut persister.
2. Choisir **Plat**, puis démarrer un morceau connu en sortie PCM/stéréo, volume modéré. Comparer une seule bande à 0 puis +2 dB. Désactiver les autres traitements dynamiques disponibles sur le lecteur, la TV ou l'ampli pour isoler la cause.
3. Si aucun effet : essayer **Portée : lecteurs compatibles**, puis arrêter/relancer la lecture dans l'autre application. Vérifier le compteur. Ne pas superposer les deux portées.
4. Comparer EQ activé/désactivé, plusieurs morceaux et applications, haut-parleurs, Bluetooth, HDMI PCM et passthrough. Tester aussi fermeture/réouverture de l'écran et arrêt du service.
5. Pour une vérification objective, utiliser un signal multiton à faible niveau et mesurer la sortie de la box : vérifier le niveau des composantes non modifiées. Les tests CI ne remplacent pas cette mesure et ne certifient pas une baisse de volume résolue sur matériel.

## Télécommande et sauvegarde

OK entre/sort du réglage. En réglage : haut/bas ajuste la bande de 1 dB. En navigation : haut revient à EQ, bas aux profils, gauche/droite change de bande. Retour quitte d'abord le réglage puis ferme l'écran. Les préférences sont automatiquement conservées ; les effets appartiennent à un service de premier plan, avec notification et arrêt explicite. Pas de démarrage au boot. Après un arrêt forcé, rouvrir OctoEQ. Le fabricant peut interrompre le service.

Les boutons de portée, de moteur et Diagnostic/aide décrivent le fonctionnement réel. Le graphique est décoratif. Le surround Virtualizer n'est pas du 5.1 et est déprécié depuis API 35.

## Compilation et APK

JDK 17, SDK 35, Gradle 8.11.1 : `gradle testDebugUnitTest lintDebug assembleDebug` (pas de wrapper livré). GitHub Actions exécute tests, lint, compilation, vérification de signature et manifeste TV/service ; un push main réussi publie une préversion, APK et SHA256SUMS.

APK de test signé debug : la clé CI peut changer ; une désinstallation de l'ancien APK peut être nécessaire, avec perte des réglages. Une signature de distribution durable n'est pas encore configurée. Déclaration `specialUse` à faire examiner pour une éventuelle publication Play Store.

## Sources primaires

- [Equalizer : session 0 dépréciée](https://developer.android.com/reference/android/media/audiofx/Equalizer)
- [Annonces des sessions audio](https://developer.android.com/reference/android/media/audiofx/AudioEffect#ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
- [AOSP LVM : EqualizerLimitBandLevels et gainCorrection](https://android.googlesource.com/platform/frameworks/av/+/12834fe289e8173e94f31603fa6d313abe56ad70/media/libeffects/lvm/wrapper/Bundle/EffectBundle.cpp)
- [Configuration DynamicsProcessing](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing.Config.Builder)
- [Bandes et fréquences de coupure](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing.EqBand)
- [Services de premier plan](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
