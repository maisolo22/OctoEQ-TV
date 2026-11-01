# OctoEQ TV 0.3 — moteur fidèle et portée audio

## Propriété des effets
`AudioService` est l'unique propriétaire et auteur des réglages natifs. `MainActivity` lit les capacités et écrit les préférences. Une mise à jour est coalescée sur 16 ms, puis seuls les coefficients EQ modifiés sont transmis ; l'état activé n'est changé que s'il diffère. La perte/reprise de contrôle invalide le cache pour rétablir la courbe. Les erreurs de création et d'application apparaissent au diagnostic.

## Moteurs
Mode fidèle par défaut : `DynamicsProcessing` API 28+, dix bandes de pré-EQ, entrée 0 dB, MBC/post-EQ/limiteur absents. Vérification de la configuration relue auprès du moteur ; un refus revient à `Equalizer` avec avertissement. Aucune garantie sur un traitement dynamique ajouté après l'effet par le firmware, un lecteur, la TV ou un ampli. BassBoost et Virtualizer ne sont pas instanciés en mode fidèle. Les bandes fidèles sont initialement plates et séparées des anciens réglages matériels, conservés.

Compatibilité : `Equalizer`, BassBoost et Virtualizer Android selon capacités. Une compensation de volume interne peut subsister. L'application n'essaie pas de la masquer avec LoudnessEnhancer, AGC, augmentation du volume système ou décalage automatique des autres bandes.

## Portées mutuellement exclusives
- Globale : une chaîne sur session 0, expérimentale et dépréciée pour les effets d'insertion.
- Lecteurs compatibles : annonce de sessions via les broadcasts officiels reçus pendant que le service fonctionne. Une chaîne par session annoncée, bornée à 16, avec déduplication des propriétaires et libération à la clôture. Les identifiants non positifs sont ignorés. Les extras sont des données non fiables et n'autorisent ni exécution de commande ni lancement d'un composant arbitraire.

La portée globale n'est jamais superposée à celle des sessions. Les lecteurs n'émettant pas les annonces ne sont pas découverts. Si plusieurs sessions ont des moteurs différents, leurs coefficients sont interpolés en fréquence à partir du moteur affiché. Changer de moteur principal peut rappeler sa courbe enregistrée ; vérifier Plat pour les comparaisons.

## Limites
Ni root, ni capture/relecture de l'audio, ni intégration octoStream. Pas de garantie toutes applications, HDMI passthrough, offload ou multicanal. Une intégration DSP au lecteur octoStream demanderait des changements explicites de ce lecteur, sans prétendre traiter les autres apps. Voir README pour les sources AOSP et la procédure d'essai sur Mi Box.

## Validation
Tests de navigation, persistance, configuration sans compression/limiteur, gain général fixe, mises à jour isolées, interpolation entre moteurs et gestion des sessions. CI : tests, lint, APK, signature et manifeste. La réponse acoustique doit être mesurée sur la box réelle ; CI n'en fournit pas de preuve.
