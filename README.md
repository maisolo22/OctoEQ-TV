# OctoEQ TV 0.3.0 — Studio et sessions Android

Égaliseur indépendant pour Android TV, Android 7/API 24 minimum. Interface compacte sombre, dix contrôles de tonalité, courbe des gains demandés, préréglages, comparaison temporaire et diagnostic de compatibilité. Le graphique n’est pas un analyseur du son externe.

[Installation et tests Android Studio](ANDROID_STUDIO.md).

## Pourquoi le son diffère d’octoStream

octoStream reçoit son propre PCM et lui applique dix filtres RBJ, préampli, largeur stéréo M/S, balance et limiteur. OctoEQ ne reçoit pas le PCM de Spotify, Nuvio ou Stremio. Il demande aux moteurs Android de traiter les sessions audio accessibles. Les algorithmes, fréquences, nombre de bandes et sorties pris en charge peuvent être différents. Des courbes proches ne garantissent pas le même rendu.

## Moteurs et modes réels

| Mode | Comportement |
| --- | --- |
| Automatique | Sessions annoncées par les lecteurs ; sinon sortie globale expérimentale |
| Sessions uniquement | Attend les annonces Android OPEN_AUDIO_EFFECT_CONTROL_SESSION ou le panneau d’effets ouvert par un lecteur |
| Global expérimental | Session 0 dépréciée : dépend entièrement de la box et de sa sortie |

Les sessions et le global ne sont jamais attachés simultanément. Fermeture annoncée : libération des effets correspondants. Huit sessions maximum. Pas de scan arbitraire des identifiants, pas de capture audio. Après un redémarrage du service, relancer la lecture externe pour renouveler les annonces.

Sur session non nulle et Android 9/API 28+, tentative DynamicsProcessing avec dix bandes, préampli et limiteur liés aux canaux. Repli Equalizer Android en cas d’indisponibilité. Dans ce repli, les dix contrôles sont interpolés sur les bandes réellement exposées par le matériel : ce ne sont pas dix bandes natives indépendantes. Préampli approché par atténuation commune des bandes, sans limiteur PCM. Le diagnostic donne le moteur réel et son contrôle, pas une certification du chemin audio.

Préréglages plus modérés inspirés des courbes d’octoStream ; chaque préréglage remet BassBoost/Virtualizer à zéro pour éviter de les cumuler involontairement. Protection automatique : réserve de gain égale au plus grand boost demandé, ou préampli manuel plus faible. Elle ne garantit pas l’absence de saturation dans une chaîne OEM, notamment avec BassBoost ajouté après le DSP.

BassBoost et Virtualizer dépendent du matériel et des droits de contrôle. Virtualizer est déprécié depuis API 35 et ne reproduit ni la largeur M/S, ni la balance d’octoStream, ni un surround multicanal. Aucun réglage factice de largeur n’est ajouté.

## Interface et télécommande

OK entre dans le réglage d’une bande ; OK valide. Haut/bas modifie le gain en réglage, sinon rejoint alimentation/préréglages. Gauche/droite quitte le réglage et change de bande. Retour quitte d’abord le réglage. Gain sauvegardé automatiquement. La courbe montre les gains demandés, pas une mesure acoustique.

Applications / sortie choisit le mode ou ouvre le diagnostic. Rebrancher les effets retente la connexion aux sessions connues. Ouvrir une application liste les applications TV installées. Comparer avec l’original désactive temporairement les effets détenus par OctoEQ ; la comparaison se termine quand l’écran est quitté. Le service garde les réglages pendant l’écoute externe.

Réglages enregistrés dans SharedPreferences ; anciens band_N migrés vers les gains de référence lorsque les fréquences matérielles sont accessibles. Service spécial premier plan démarré par l’utilisateur ; pas de démarrage au boot. Android ou le fabricant peut l’arrêter. Le bouton Arrêter libère les effets après fermeture de l’écran.

## Compatibilité Spotify / Nuvio / Stremio

Ouvrir OctoEQ avant la lecture, commencer en Auto puis relancer la piste externe. Vérifier le diagnostic. Les applications qui n’annoncent pas de session ne peuvent pas être prises en charge de manière fiable. Pour les vidéos, essayer PCM et passthrough désactivé ; HDMI bitstream et certains chemins matériels/DRM peuvent contourner les effets. Tester chaque application et chaque sortie séparément. Aucun résultat sur box réelle n’est certifié par les tests logiciels.

## Compilation

JDK 17 minimum, SDK 35, Gradle wrapper 8.11.1. Windows :

```powershell
.\gradlew.bat testDebugUnitTest lintDebug :app:assembleDebug -PoctoPreview=true
```

macOS/Linux : bash gradlew testDebugUnitTest lintDebug :app:assembleDebug -PoctoPreview=true.
L’option preview crée OctoEQ TV Test à côté de l’app normale. La signature debug locale reste stable sur votre poste ; les APK CI signés avec une clé éphémère ne garantissent pas les mises à jour entre runners. Une signature Release durable est nécessaire avant distribution stable.

## Sources

- https://developer.android.com/reference/android/media/audiofx/AudioEffect
- https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing
- https://developer.android.com/reference/android/media/audiofx/Virtualizer
- https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
