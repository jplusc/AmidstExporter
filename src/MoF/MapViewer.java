package MoF;


import amidst.Options;
import amidst.gui.menu.PlayerMenuItem;
import amidst.gui.menu.AmidstMenu.DisplayingCheckbox;
import amidst.logging.Log;
import amidst.map.FragmentManager;
import amidst.map.IconLayer;
import amidst.map.ImageLayer;
import amidst.map.LiveLayer;
import amidst.map.Map;
import amidst.map.MapMarkers;
import amidst.map.MapObject;
import amidst.map.MapObjectPlayer;
import amidst.map.WorldDimensionType;
import amidst.map.layers.BiomeIconLayer;
import amidst.map.layers.BiomeLayer;
import amidst.map.layers.EndCityLayer;
import amidst.map.layers.EndIslandsLayer;
import amidst.map.layers.GridLayer;
import amidst.map.layers.MineshaftLayer;
import amidst.map.layers.NetherFortressLayer;
import amidst.map.layers.OceanMaskLayer;
import amidst.map.layers.OceanMonumentLayer;
import amidst.map.layers.PlayerLayer;
import amidst.map.layers.SlimeLayer;
import amidst.map.layers.SpawnLayer;
import amidst.map.layers.StrongholdLayer;
import amidst.map.layers.TempleLayer;
import amidst.map.layers.VillageLayer;
import amidst.map.widget.BiomeToggleWidget;
import amidst.map.widget.BiomeWidget;
import amidst.map.widget.CursorInformationWidget;
import amidst.map.widget.DebugWidget;
import amidst.map.widget.FpsWidget;
import amidst.map.widget.ScaleWidget;
import amidst.map.widget.PanelWidget.CornerAnchorPoint;
import amidst.map.widget.PleaseWaitWidget;
import amidst.map.widget.SeedWidget;
import amidst.map.widget.SelectedObjectWidget;
import amidst.map.widget.Widget;
import amidst.minecraft.MinecraftUtil;
import amidst.resources.ResourceLoader;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

public class MapViewer extends JComponent implements MouseListener, MouseWheelListener, KeyListener {
	private static final long serialVersionUID = -8309927053337294612L;
	// TODO: This should likely be moved somewhere else.
	private FragmentManager fragmentManager;
	private static PlayerLayer    playerLayer;	
	
	private Widget mouseOwner;
	private static BufferedImage
		dropShadowBottomLeft  = ResourceLoader.getImage("dropshadow/inner_bottom_left.png"),
		dropShadowBottomRight = ResourceLoader.getImage("dropshadow/inner_bottom_right.png"),
		dropShadowTopLeft     = ResourceLoader.getImage("dropshadow/inner_top_left.png"),
		dropShadowTopRight    = ResourceLoader.getImage("dropshadow/inner_top_right.png"),
		dropShadowBottom      = ResourceLoader.getImage("dropshadow/inner_bottom.png"),
		dropShadowTop         = ResourceLoader.getImage("dropshadow/inner_top.png"),
		dropShadowLeft        = ResourceLoader.getImage("dropshadow/inner_left.png"),
		dropShadowRight       = ResourceLoader.getImage("dropshadow/inner_right.png"),
		endVoidTexture        = ResourceLoader.getImage("void.png");
	private static TexturePaint 
		endVoidTexturePaint   = new TexturePaint(endVoidTexture, new Rectangle(0, 0, endVoidTexture.getWidth(), endVoidTexture.getHeight()));
	
	
	private Project proj;
	
	private JPopupMenu menu = new JPopupMenu();
	public int strongholdCount, villageCount;
	
	private Map worldMap;
	private MapObject selectedObject = null;
	private Point lastMouse;
	public Point lastRightClick = null;
	private Point2D.Double panSpeed;
	public MapExporter exporter;	
	
	private static int zoomLevel = 0, zoomTicksRemaining = 0;
	private static double targetZoom = 0.25f, curZoom = 0.25f;
	private Point zoomMouse = new Point();
	
	private Font textFont = new Font("arial", Font.BOLD, 15);
	
	private FontMetrics textMetrics;
	
	private ArrayList<Widget> widgets = new ArrayList<Widget>();
	private long lastTime;
	
	public void dispose() {
		Log.debug("Disposing of map viewer.");
		setWorldDimension(WorldDimensionType.NONE); // disposes worldmap and fragmentManager then doesn't create new ones.
		exporter.dispose();
		menu.removeAll();
		proj = null;
	}
	
	MapViewer(Project proj) {

		panSpeed = new Point2D.Double();
		this.proj = proj;
		
		playerLayer = new PlayerLayer();		
		if (playerLayer.isEnabled = proj.saveLoaded) {
			playerLayer.setPlayers(proj.save);
			for (MapObjectPlayer player : proj.save.getPlayers()) {
				menu.add(new PlayerMenuItem(this, player, playerLayer));
			}
		}
		
		updateWorldDimension(); // creates worldMap and fragmentManager
				
		exporter = new MapExporter(Options.instance.seed);			
		
		widgets.add(new FpsWidget(this).setAnchorPoint(CornerAnchorPoint.BOTTOM_LEFT));
		widgets.add(new ScaleWidget(this).setAnchorPoint(CornerAnchorPoint.BOTTOM_CENTER));
		widgets.add(new SeedWidget(this).setAnchorPoint(CornerAnchorPoint.TOP_LEFT));
		widgets.add(new DebugWidget(this).setAnchorPoint(CornerAnchorPoint.BOTTOM_RIGHT));
		widgets.add(new SelectedObjectWidget(this).setAnchorPoint(CornerAnchorPoint.TOP_LEFT));
		widgets.add(new CursorInformationWidget(this).setAnchorPoint(CornerAnchorPoint.TOP_RIGHT));
		widgets.add(new BiomeToggleWidget(this).setAnchorPoint(CornerAnchorPoint.BOTTOM_RIGHT));
		widgets.add(new PleaseWaitWidget(this).setAnchorPoint(CornerAnchorPoint.CENTER));
		widgets.add(BiomeWidget.get(this).setAnchorPoint(CornerAnchorPoint.NONE));
		addMouseListener(this);
		addMouseWheelListener(this);
		
		setFocusable(true);
		lastTime = System.currentTimeMillis();

		textMetrics = getFontMetrics(textFont);
	}

	/** invokes setWorldDimension() with the dimension currently selected in the options */ 
	private void updateWorldDimension() {
				
		WorldDimensionType desiredDimension = Options.instance.showEndChunks.get() ? WorldDimensionType.THEEND : WorldDimensionType.OVERWORLD;
		
		if (worldMap == null || worldMap.getWorldDimension() != desiredDimension) {
			setWorldDimension(desiredDimension);			
		}		
	}
	
	public void setWorldDimension(WorldDimensionType mapType) {
		
		fragmentManager = null;
		if (worldMap != null) {
			worldMap.dispose(); // this will also clean up the fragmentManager (passed to it during construction)
			worldMap = null;
		}

		if (mapType != WorldDimensionType.NONE) {
			fragmentManager = createFragmentManager(mapType); 
			worldMap = new Map(fragmentManager); //TODO: implement more layers
			worldMap.setZoom(curZoom);
		}
	}
	
	
	private FragmentManager createFragmentManager(WorldDimensionType mapType) {
		
		FragmentManager result = null;
		
		if (mapType == WorldDimensionType.OVERWORLD) {
		
			result = new FragmentManager(
				mapType,
				new ImageLayer[] {
					new BiomeLayer(),
					new SlimeLayer()
				},
				new LiveLayer[] {
					new GridLayer()
				},
				new IconLayer[] {
					new VillageLayer(),
					new OceanMonumentLayer(),
					new StrongholdLayer(),
					new TempleLayer(),
					new SpawnLayer(),
					new NetherFortressLayer(),
					new MineshaftLayer(),
					
					// Including BiomeIconLayers in the MapViewer is only useful for debug purposes,
					// there's no need to display this data in the main window - the BiomeIconLayers 
					// are for MapExporter.
					// Plus you have to move the biome off screen then back onto the screen in order
					// to remove the excess mapObjects created for each fragment before they were
					// condensed into one.
					//
					// new BiomeIconLayer(MapMarkers.RAREBIOME_MUSHROOM_ISLAND),
					// new BiomeIconLayer(MapMarkers.RAREBIOME_ICE_PLAINS_SPIKES),
					// new BiomeIconLayer(MapMarkers.RAREBIOME_FLOWER_FOREST),
					// new BiomeIconLayer(MapMarkers.RAREBIOME_MESA),
					
					playerLayer
				}
			);
		
		} else if (mapType == WorldDimensionType.THEEND) {
			
			result = new FragmentManager(
				mapType,
				new ImageLayer[] {
					new EndIslandsLayer()
				},
				new LiveLayer[] {
					new GridLayer()
				},
				new IconLayer[] {
					new EndCityLayer()
				}
			);		
		}		
		
		return result;
	}
	
	@Override
	public void paint(Graphics g) {	 
		Graphics2D g2d = (Graphics2D)g.create();
				
		long currentTime = System.currentTimeMillis();
		float time = Math.min(Math.max(0, currentTime - lastTime), 100) / 1000.0f;
		lastTime = currentTime;
		
		updateWorldDimension(); // incase a menu selection has changed the options.
		if (worldMap.getWorldDimension() == WorldDimensionType.THEEND) {
			// Draw the End Sky
			// ToDo: if you move the texture around randomly it should look more like the void
			//       (but TexturePaint is aligned with the g2d raster rather than something controllable)			
			g2d.setPaint(endVoidTexturePaint);			
		} else {
			g2d.setColor(Color.black);			
		}
		g2d.fillRect(0, 0, this.getWidth(), this.getHeight());

		if (zoomTicksRemaining-- > 0) {
			double lastZoom = curZoom;
			curZoom = (targetZoom + curZoom) * 0.5;
			
			if(zoomMouse != null) {
				Point2D.Double targetZoom = worldMap.getScaled(lastZoom, curZoom, zoomMouse);
				worldMap.moveBy(targetZoom);
				worldMap.setZoom(curZoom);
			}
		}
		
		if (lastMouse != null) {
			Point curMouse = getMousePosition();
			if (curMouse != null) {
				double difX = curMouse.x - lastMouse.x;
				double difY = curMouse.y - lastMouse.y;
				// TODO : Scale with time
				panSpeed.setLocation(difX * 0.2, difY * 0.2);
			}
			
			lastMouse.translate((int) panSpeed.x, (int)panSpeed.y);
		}

		worldMap.moveBy((int)panSpeed.x, (int)panSpeed.y);
		if (Options.instance.mapFlicking.get()) {
			panSpeed.x *= 0.95f;
			panSpeed.y *= 0.95f;
		} else {
			panSpeed.x *= 0.f;
			panSpeed.y *= 0.f;
		}
		
		worldMap.width = getWidth();
		worldMap.height = getHeight();
		
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		worldMap.draw((Graphics2D)g2d.create(), time);
		g2d.drawImage(dropShadowTopLeft,     0,               0,                null);
		g2d.drawImage(dropShadowTopRight,    getWidth() - 10, 0,                null);
		g2d.drawImage(dropShadowBottomLeft,  0,               getHeight() - 10, null);
		g2d.drawImage(dropShadowBottomRight, getWidth() - 10, getHeight() - 10, null);
		
		g2d.drawImage(dropShadowTop,    10, 0, getWidth() - 20, 10,  null);
		g2d.drawImage(dropShadowBottom, 10, getHeight() - 10, getWidth() - 20, 10,  null);
		g2d.drawImage(dropShadowLeft,   0, 10, 10, getHeight() - 20, null);
		g2d.drawImage(dropShadowRight,  getWidth() - 10, 10, 10, getHeight() - 20, null);

		g2d.setFont(textFont);
		for (Widget widget : widgets) {
			if (widget.isVisible()) {
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, widget.getAlpha()));
				widget.draw(g2d, time);
			}
		}
	}
	
	
	public void centerAt(long x, long y) {
		worldMap.centerOn(x, y);
	}
	
	public void adjustZoom(Point position, int notches) {
		zoomMouse = position;
		if (notches > 0) {
			if (zoomLevel < (Options.instance.maxZoom.get()?10:10000)) {
				targetZoom /= 1.1;
				zoomLevel++;
				zoomTicksRemaining = 100;
			}
		} else {
			if (zoomLevel > -20) {
				targetZoom *= 1.1;
				zoomLevel--;
				zoomTicksRemaining = 100;
			}
		}
	}
	
	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		int notches = e.getWheelRotation();
		Point mouse = e.getPoint(); // Don't use getMousePosition() because when computer is swapping/grinding, mouse may have moved out of window before execution reaches here.
		for (Widget widget : widgets) {
			if ((widget.isVisible()) &&
				(mouse.x > widget.getX()) &&
				(mouse.y > widget.getY()) &&
				(mouse.x < widget.getX() + widget.getWidth()) &&
				(mouse.y < widget.getY() + widget.getHeight())) {
				if (widget.onMouseWheelMoved(mouse.x - widget.getX(), mouse.y - widget.getY(), notches))
					return;
			}
		}
		adjustZoom(mouse, notches);
	}
	@Override
	public void mouseClicked(MouseEvent e) {
		if (!e.isMetaDown()) {
			Point mouse = e.getPoint(); // Don't use getMousePosition() because when computer is swapping/grinding, mouse may have moved out of window before execution reaches here.
			for (Widget widget : widgets) {
				if ((widget.isVisible()) &&
					(mouse.x > widget.getX()) &&
					(mouse.y > widget.getY()) &&
					(mouse.x < widget.getX() + widget.getWidth()) &&
					(mouse.y < widget.getY() + widget.getHeight())) {
					if (widget.onClick(mouse.x - widget.getX(), mouse.y - widget.getY()))
						return;
				}
			}
			MapObject object = worldMap.getObjectAt(mouse, 50.0);
			
			if (selectedObject != null)
				selectedObject.localScale = 1.0;

			if (object != null)
				object.localScale = 1.5;
			selectedObject = object;
		}
	}
	
	
	@Override
	public void mouseEntered(MouseEvent arg0) {
	}
	@Override
	public void mouseExited(MouseEvent arg0) {
	}
	@Override
	public void mousePressed(MouseEvent e) {
		if (e.isMetaDown())
			return;
		Point mouse = e.getPoint(); // Don't use getMousePosition() because when computer is swapping/grinding, mouse may have moved out of window before execution reaches here.
		for (Widget widget : widgets) {
			if ((widget.isVisible()) &&
				(mouse.x > widget.getX()) &&
				(mouse.y > widget.getY()) &&
				(mouse.x < widget.getX() + widget.getWidth()) &&
				(mouse.y < widget.getY() + widget.getHeight())) {
				if (widget.onMousePressed(mouse.x - widget.getX(), mouse.y - widget.getY())) {
					mouseOwner = widget;
					return;
				}
			}
		}
		lastMouse = mouse;
	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		if (e.isPopupTrigger() && MinecraftUtil.getVersion().saveEnabled()) {
			lastRightClick = e.getPoint(); // Don't use getMousePosition() because when computer is swapping/grinding, mouse may have moved out of window before execution reaches here.
			if (proj.saveLoaded) {
				menu.show(e.getComponent(), e.getX(), e.getY());
			}
		} else {
			if (mouseOwner != null) {
				mouseOwner.onMouseReleased();
				mouseOwner = null;
			} else {
				lastMouse = null;
			}
		}
	}
	
	public MapObject getSelectedObject() {
		return selectedObject;
	}
	
	
	public void movePlayer(String name, ActionEvent e) {
		//PixelInfo p = getCursorInformation(new Point(tempX, tempY));
		
		//proj.movePlayer(name, p);
	}
	
	public void saveToFile(File f) {
		BufferedImage image = new BufferedImage(worldMap.width, worldMap.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		
		if (worldMap.getWorldDimension() == WorldDimensionType.THEEND) {
			// Draw the End Sky
			g2d.setPaint(endVoidTexturePaint);
			g2d.fillRect(0, 0, this.getWidth(), this.getHeight());
		}
		worldMap.draw(g2d, 0);

		for (Widget widget : widgets)
			if (widget.isVisible())
				widget.draw(g2d, 0);
		
		try {
			ImageIO.write(image, "png", f);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		g2d.dispose();
		image.flush();
	}

	public void saveNetherLocationsToFile(File f) {
		exporter.saveNetherLocationsToFile(f);		
	}
	
	public void saveEndLocationsToFile(File f) {
		exporter.saveEndLocationsToFile(f);		
	}
	public void saveLocationsToFile(File f) {
		exporter.saveOverworldLocationsToFile(f);		
	}
	
	public void saveOceanToFile(File f) {
		exporter.saveOceanToFile(f);
	}
	
	
	@Override
	public void keyPressed(KeyEvent e) {
		// TODO Auto-generated method stub
		Point mouse = getMousePosition();
		if (mouse == null)
			mouse = new Point(getWidth() >> 1, getHeight () >> 1);
		if (e.getKeyCode() == KeyEvent.VK_EQUALS)
				adjustZoom(mouse, -1);
		else if (e.getKeyCode() == KeyEvent.VK_MINUS)
				adjustZoom(mouse, 1);
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	public FragmentManager getFragmentManager() {
		return fragmentManager;
	}

	/** 
	 * Don't cache this value (unless you're prepared to monitor for changes 
	 * to which WorldDimension is being displayed by the MapViewer). 
	 */
	public Map getMap() {
		return worldMap;
	}
	
	public FontMetrics getFontMetrics() {
		return textMetrics;
	}
}
