# OctoEQ TV — mode C (hybride)

## Mode 1 — Global expérimental
Le prototype autonome utilise les effets Android Equalizer/BassBoost/Virtualizer sur la session 0. Cette session est dépréciée et peut ne produire aucun effet sur certaines TV ou applications. Ne pas promettre d'agir sur Netflix/YouTube/Kodi. Les flux audio HDMI passthrough ne sont généralement pas modifiables par ces effets.

## Mode 2 — octoStream optimisé
Le Studio Audio de l'application octoStream Android TV possède déjà un DSP PCM : `tv/src/main/java/com/example/tv/audio/StudioDsp.kt` et `StudioAudioProcessor.kt`, avec 10 bandes, préampli, largeur stéréo, balance et limiteur. C'est le chemin recommandé pour la lecture octoStream. Les réglages sont enregistrés localement dans octoStream.

## Limite importante
Deux APK distincts ne peuvent pas simplement partager un AudioSessionId ou modifier arbitrairement le flux PCM de l'autre. Une intégration OctoEQ -> octoStream demandera un protocole explicite (service/broadcast protégé ou ContentProvider), une vérification de la signature et des changements dans octoStream. **Ce lien n'est pas encore implémenté**. En attendant, ouvrir Studio audio dans octoStream pour le mode optimisé.

## Validation requise
1. Compiler le projet companion-eq avec Android Studio.
2. Tester le mode global sur la TV cible, haut-parleurs TV, HDMI et Bluetooth.
3. Vérifier les interactions télécommande et la persistance des réglages.
4. Ajouter un mécanisme explicite et sécurisé de synchronisation avec octoStream si nécessaire.
5. Créer un dépôt indépendant et un pipeline APK signé après validation.
