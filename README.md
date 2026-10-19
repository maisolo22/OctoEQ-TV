# OctoEQ TV 0.2.0

Studio audio indépendant pour Android TV (Android 7.0/API 24 minimum). Interface sombre, curseurs colorés répartis horizontalement, profils, basses et spatialisation virtuelle. Le graphique supérieur est décoratif, pas une mesure du son.

## Télécommande
- OK entre dans le réglage de la bande ; OK à nouveau valide et revient à la navigation.
- En réglage : haut/bas modifie le gain de 1 dB, dans les limites du matériel.
- En navigation : haut revient au bouton EQ, bas aux profils, gauche/droite change de bande.
- Retour quitte d'abord le réglage ; un autre Retour ferme l'écran en conservant le service.
- Les préréglages recréent les bandes avec leurs sorties de focus. Les gains sont interpolés sur les fréquences réelles du matériel.

## Audio et sauvegarde
Les réglages sont enregistrés automatiquement dans SharedPreferences et restaurés à l'ouverture ainsi qu'à la recréation du service. Le bouton EQ suspend les effets sans effacer les réglages. Réinitialiser remet bandes, basses et surround à zéro. Les commandes d'intensité sont désactivées si le matériel ne les prend pas en charge.

Un service de premier plan `specialUse`, démarré lorsque l'utilisateur ouvre l'application, conserve les effets après fermeture de l'écran. Une notification permet de revenir à l'application ou d'arrêter le service. Le bouton « Arrêter le service » désactive les effets et ferme l'écran. Aucun démarrage automatique au boot ; après arrêt forcé, il faut rouvrir l'application. Android ou le fabricant peut interrompre le service. Une éventuelle publication Play Store exige la déclaration et l'examen de ce cas `specialUse`.

## Limites vérifiables
- Session audio 0 **dépréciée**, global **expérimental** : aucune garantie pour YouTube, Netflix, Kodi ou toutes les applications.
- Le nombre de bandes, les fréquences et la plage de gain viennent du moteur Android. L'application ne fabrique pas de bandes supplémentaires.
- BassBoost et Virtualizer dépendent du moteur, de la sortie et du contrôle accordé par Android. Spatialisation virtuelle, pas du 5.1 réel ; Virtualizer est déprécié depuis API 35.
- Le passthrough HDMI/bitstream peut contourner les effets ; essayer une sortie PCM. Un contrôle acquis ne prouve pas que le son externe est traité.
- Aucun accès arbitraire au PCM des autres applications. Une intégration DSP dans le lecteur, ou une solution privilégiée système/root, est nécessaire pour un contrôle plus fiable. Aucun lien octoStream n'est implémenté.
- Pas de limiteur ni de préampli PCM dans ce mode : les forts boosts cumulés peuvent saturer. Réduire les gains en cas de distorsion.

## APK et validation
L'onglet Actions exécute tests JVM/Robolectric, lint, compilation, vérification de signature APK et inspection du manifeste TV/service. Chaque push main réussi publie une préversion avec APK et SHA256SUMS. APK **de test signé debug** ; la clé CI peut changer entre runners, impliquant une désinstallation (et perte des préférences) avant mise à jour. Une clé de signature durable reste nécessaire pour une distribution stable.

Compilation locale : JDK 17, SDK 35, Gradle 8.11.1 ; `gradle testDebugUnitTest lintDebug assembleDebug`. Aucun wrapper Gradle n'est encore livré.

Les tests automatisés ne remplacent pas les essais réels : télécommande, HDMI PCM/passthrough, Bluetooth, haut-parleurs, deux applications audio concurrentes, fermeture/réouverture, arrêt du service et redémarrage TV. La compatibilité sonore globale sur matériel n'est pas certifiée.

## Sources Android
- [Equalizer et session 0](https://developer.android.com/reference/android/media/audiofx/Equalizer)
- [AudioEffect et contrôle des sessions](https://developer.android.com/reference/android/media/audiofx/AudioEffect)
- [Virtualizer](https://developer.android.com/reference/android/media/audiofx/Virtualizer)
- [Services de premier plan specialUse](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
- [Applications TV](https://developer.android.com/training/tv/start/start)
