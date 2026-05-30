import SwiftUI

/// Animated tree that grows with the learning progress fraction (0…1).
/// Mirrors the Android `LearningTreeCard`: a seedling that sprouts a trunk and
/// up to six leaf clusters as personalisation progresses, with a sun accent
/// appearing past the halfway mark. Colours come from the shared `AppTheme`
/// palette so the illustration matches the rest of the app.
struct LearningTreeView: View {
    /// 0.0 – 1.0
    let progress: Float

    @State private var animated: CGFloat = 0

    var body: some View {
        Canvas { context, size in
            let p = animated
            let w = size.width
            let h = size.height
            let groundY = h * 0.92
            let centerX = w * 0.5

            // Ground line
            var ground = Path()
            ground.move(to: CGPoint(x: w * 0.1, y: groundY))
            ground.addLine(to: CGPoint(x: w * 0.9, y: groundY))
            context.stroke(ground, with: .color(AppTheme.primary.opacity(0.25)), lineWidth: 2)

            // Sun accent past halfway
            if p >= 0.5 {
                let sunAlpha = Double(min((p - 0.5) / 0.2, 1))
                let sun = Path(ellipseIn: CGRect(x: w * 0.78, y: h * 0.08, width: 18, height: 18))
                context.fill(sun, with: .color(AppTheme.tertiary.opacity(0.85 * sunAlpha)))
            }

            // Trunk grows with progress
            let trunkHeight = h * 0.45 * CGFloat(max(p, 0.08))
            let trunkTop = groundY - trunkHeight
            var trunk = Path()
            trunk.move(to: CGPoint(x: centerX, y: groundY))
            trunk.addLine(to: CGPoint(x: centerX, y: trunkTop))
            context.stroke(
                trunk,
                with: .color(AppTheme.bark),
                style: StrokeStyle(lineWidth: max(3, 8 * CGFloat(p)), lineCap: .round)
            )

            // Leaf clusters appear progressively (6 total)
            let canopyRadius = (8 + 26 * CGFloat(p))
            let clusterCount = 6
            let visible = Int((CGFloat(clusterCount) * CGFloat(p)).rounded(.up))
            let positions: [CGPoint] = [
                CGPoint(x: centerX,            y: trunkTop),
                CGPoint(x: centerX - canopyRadius * 0.7, y: trunkTop + canopyRadius * 0.3),
                CGPoint(x: centerX + canopyRadius * 0.7, y: trunkTop + canopyRadius * 0.3),
                CGPoint(x: centerX - canopyRadius * 0.4, y: trunkTop - canopyRadius * 0.5),
                CGPoint(x: centerX + canopyRadius * 0.4, y: trunkTop - canopyRadius * 0.5),
                CGPoint(x: centerX,            y: trunkTop - canopyRadius * 0.8),
            ]
            for i in 0..<min(visible, clusterCount) {
                let r = canopyRadius * (i == 0 ? 1.0 : 0.7)
                let rect = CGRect(x: positions[i].x - r, y: positions[i].y - r, width: r * 2, height: r * 2)
                context.fill(
                    Path(ellipseIn: rect),
                    with: .color(AppTheme.primary.opacity(0.6 + 0.2 * Double(p)))
                )
            }
        }
        .frame(height: 88)
        .onAppear { withAnimation(.easeOut(duration: 0.8)) { animated = CGFloat(progress) } }
        .onChange(of: progress) { newValue in
            withAnimation(.easeOut(duration: 0.8)) { animated = CGFloat(newValue) }
        }
    }
}
