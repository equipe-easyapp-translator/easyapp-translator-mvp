# Design system do EasyApp

O visual do app segue o design system do **Panela** (TCC do Brayan), portado de Flutter para Jetpack Compose. Tudo o que é cor, fonte, espaçamento e componente sai daqui. Não use cor ou tamanho soltos numa tela.

## Onde está

| Arquivo | O que tem |
| :-- | :-- |
| `ui/theme/EasyColors.kt` | Tokens de cor, claro e escuro |
| `ui/theme/EasyType.kt` | Fonte Archivo e escala tipográfica |
| `ui/theme/EasySpacing.kt` | Espaçamentos (`Space`) e raios (`Radius`) |
| `ui/theme/Theme.kt` | `EasyAppTheme` e o atalho `EasyTheme.colors` / `EasyTheme.type` |
| `ui/components/EasyComponents.kt` | `EasyCard`, `EasyButton`, `EasyChip`, `StatusBadge`, `IconBubble`, `InfoNote`, `Overline`, `Hint`, `StatusRow`, `CardHeader` |
| `ui/home/` | A tela principal, dividida em `HomeScreen`, `SetupCards`, `SessionCards` e `Dialogs` |
| `res/drawable/ic_ui_*.xml` | Ícones de interface no estilo Lucide (o mesmo pacote do Panela) |

## Regras

- **Verde só para marca, ação primária e estado positivo** (sessão ativa, permissão concedida, modelo pronto). Nunca em fundo de tela, cabeçalho ou texto neutro.
- **Uma ação primária por tela ou cartão.** As outras usam `ButtonKind.Tonal`, `Outline` ou `Ghost`.
- A borda (`line`) separa o cartão do fundo. Não use sombra.
- O tema escuro segue o do sistema. Ao criar uma tela, teste nos dois.
- Os textos de interface ficam em português do Brasil, em tom direto. Explique as limitações sem prometer compatibilidade com todos os apps.

## Cores principais

| Token | Claro | Escuro | Uso |
| :-- | :-- | :-- | :-- |
| `paper` | `#F4F1EA` | `#191A15` | fundo de tela |
| `surface` | `#FFFEFB` | `#22241D` | cartão |
| `text` | `#1B1D18` | `#F0EEE6` | texto principal |
| `brand` | `#0E6B4D` | `#4EC294` | marca, ação primária |
| `brandSolid` | `#0E6B4D` | `#0E6B4D` | preenchimento do botão primário |
| `warn` | `#B0472A` | `#E98B64` | pendente, pausado, alerta |

## Ícone do app

O conceito é **"Dois balões"**: o balão creme com a letra A representa quem fala, e o balão verde-claro, a tradução que responde.

- `res/mipmap-anydpi-v26/ic_launcher.xml`: ícone adaptativo com fundo `#0E6B4D`.
- `res/drawable/ic_launcher_foreground.xml`: a camada da frente, reduzida a 92% para caber no círculo seguro de 66 dp.
- `res/drawable/ic_launcher_monochrome.xml`: versão em traço para os ícones temáticos do Android 13+.
- `res/drawable/ic_notification.xml`: a mesma silhueta, para a notificação da sessão.

Para mudar o desenho, edite o `pathData` nas três camadas, que usam o mesmo canvas de 108×108.

## Licenças

- Fonte Archivo: SIL Open Font License, em `docs/licencas/Archivo-OFL.txt`.
- Ícones de interface: desenhados a partir do Lucide (licença ISC).
