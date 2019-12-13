package com.auction.app.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.auction.app.dto.CreateAuctionDTO;
import com.auction.app.model.Auction;
import com.auction.app.model.AuctionImage;
import com.auction.app.model.Category;
import com.auction.app.model.User;
import com.auction.app.repository.AuctionImageRepository;
import com.auction.app.repository.AuctionRepository;
import com.auction.app.repository.CategoryRepository;
import com.auction.app.repository.UserRepository;
import com.auction.app.util.DateUtil;

@Controller
@PropertySource("auction.properties")
public class ProjectController {
	
	
	@Autowired
	CategoryRepository categoryRepository;
	
	@Autowired
	UserRepository userRepository;
	
	@Autowired
	AuctionRepository auctionRepository;
	
	@Autowired
	AuctionImageRepository auctionImageRepository;
	
	@Value("${auction.fileUpload}")
	String fileUploadURL;
	
	private static List<String> auctionImages;
	 
	private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

	
	@RequestMapping("/createAuction")
	public String projectPage(Model model) {
		Category ct0=new Category();
		ct0.setCategoryId(0);
		ct0.setCategoryName("Painting");
		ct0.setActive(0);
		
		Category ct1=new Category();
		ct1.setCategoryId(1);
		ct1.setCategoryName("Scenery");
		ct1.setActive(1);
		
		Category ct2=new Category();
		ct2.setCategoryId(2);
		ct2.setCategoryName("Nature");
		ct2.setActive(2);
		
		categoryRepository.save(ct0);
		categoryRepository.save(ct1);
		categoryRepository.save(ct2);
		model.addAttribute("categories", categoryRepository.findAll());
		return "CreateAuction";
	}
	
	@RequestMapping(path = "/createAuction", method = RequestMethod.POST)
	@ResponseBody
	public String createAuction(CreateAuctionDTO auction,HttpServletRequest request) throws IllegalStateException, IOException {
		String username=SecurityContextHolder.getContext().getAuthentication().getName();
		User user=userRepository.findByUsername(username);
		System.out.println(request.getLocalAddr());
		
		if(user!=null) {
			if(auction.isDataValid()) {
				Auction auctionDetails=new Auction(user, auction.getTitle(), auction.getStartingPrice(), LocalDateTime.now(), 
						DateUtil.getDate(auction.getEndingDate()), categoryRepository.findByCategoryId(auction.getCategory()), auction.getDescription(), 1);
				
				Auction createdAuction=auctionRepository.save(auctionDetails);
				
				//Uploading Files
			 	for(MultipartFile file : auction.getMultiPartFiles()) {
					/*
					 ************** Until any storage service is setup file will be randomly choosen
					 ************** from image folder  
						//Uploading Images
						File myFile = new File("auction/" + createdAuction.getAuctionId() );
						myFile.mkdirs();
						File realFile=new File(myFile.getAbsolutePath()+"/"+file.getOriginalFilename());
						file.transferTo(realFile);
						//Adding Image To Database
						AuctionImage image=new AuctionImage(createdAuction, file.getOriginalFilename());
						auctionImageRepository.save(image);
						
						System.out.println(realFile.getAbsolutePath());
					*******************************************************
					*****************************END
					*/
					String fileName=(String) getRandomImage();
					AuctionImage image=new AuctionImage(createdAuction, fileName);
					auctionImageRepository.save(image);
				}
			}
		}
		return "/";
	}
	
	private String getRandomImage() {
		if(auctionImages == null) {
			try (Stream<Path> walk = Files.walk(Paths.get("auction/images/"))) {
	
				auctionImages = walk.filter(Files::isRegularFile)
						.map(x -> x.toString()).collect(Collectors.toList());
			}
			catch (IOException e) {
				e.printStackTrace();
			}
				
		}
		int fileIndex = ThreadLocalRandom.current().nextInt(0, auctionImages.size());
		return auctionImages.get(fileIndex);
		
		
	}

	@RequestMapping(value = "/image/{auctionId}")
	@ResponseBody
	public byte[] getImage(@PathVariable(value = "auctionId") int auctionId) throws IOException {
		try {
			Pageable pageable=PageRequest.of(0, 1);
			AuctionImage image=auctionImageRepository.findByProject(auctionRepository.findById(auctionId).get(),pageable).get(0);
		    File serverFile = new File(image.getPath());
	
		    return Files.readAllBytes(serverFile.toPath());
		}catch (Exception e) {
//			e.printStackTrace();
			log.info("-------------------------------START-------------------------");
			log.info(">>>>>>>>>>>>>>>>>>Exception Occured >>> getImage()");
			log.info(">>>>>>>>>>>>>>>>>>Message " + e.getMessage());
			log.info("------------------------------- END -------------------------");
			return null;
		}
		
	}
	
	@RequestMapping("/ManageAuction")
	@ResponseBody
	public String getAuctionList(Model model) {
		
		String username=SecurityContextHolder.getContext().getAuthentication().getName();
		User whoAmI=userRepository.findByUsername(username);
		Iterable<Auction> au=auctionRepository.findAll();
		
		String html="";
		html+="<html xmlns:th=\"http://www.thymeleaf.org\">\r\n" + 
				"<head>\r\n" + 
				"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\">\r\n" + 
				"<title>Manage Auction</title>\r\n" + 
				"<script src=\"required/jquery.min.js\">function delAuction(id){\r\n" + 
				"	\r\n" + 
				"	 $.post(\"ManageAuction/deleteAuction/\"+id,\r\n" + 
				"	  	{\r\n" + 
				"\r\n" + 
				"	  	},\r\n" + 
				"	  	function(a){\r\n" + 
				"	  		$.notify(a,\"info\");\r\n" + 
				"	  	}\r\n" + 
				"	);\r\n" + 
				"}</script>\r\n" + 
				"<link rel=\"shortcut icon\" href=\"Images/fav.ico\" type=\"image/png\">\r\n" + 
				"<script src=\"required/bootstrap.min.js\"></script>\r\n" + 
				"<script src=\"required/notify.min.js\"></script>\r\n" + 
				"<link rel=\"stylesheet\" href=\"required/bootstrap.min.css\">\r\n" + 
				"<link rel=\"stylesheet\" href=\"css/postedbid.css\">\r\n" + 
				"\r\n" + 
				"<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css\" integrity=\"sha384-BVYiiSIFeK1dGmJRAkycuHAHRg32OmUcww7on3RYdg4Va+PmSTsz/K68vbdEjh4u\" crossorigin=\"anonymous\">\r\n" + 
				"<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap-theme.min.css\" integrity=\"sha384-rHyoN1iRsVXV4nD0JutlnGaslCJuC7uwjduW9SVrLvRYooPp2bWYgmgJQIXwl/Sp\" crossorigin=\"anonymous\">\r\n" + 
				"<script src=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js\" integrity=\"sha384-Tc5IQib027qvyjSMfHjOMaLkfuWVxZxUPnCJA7l2mCWNIpG9mGCD8wGNIcPD7Txa\" crossorigin=\"anonymous\"></script>\r\n" + 
				"\r\n" + 
				"</head>\r\n" + 
				"<body><div th:insert=\"../../../../../resources/templates/Header.html\"> </div>\r\n" + 
				"\r\n" + 
				"\r\n" + 
				"<center><h3 class=\"tittle-w3layouts my-lg-4 my-4\">Your AuctionList </h3></center>\r\n" + 
				"<div class=\"card\">\r\n" + 
				" <!--  <h3 class=\"card-header text-center font-weight-bold text-uppercase py-4\">Editable table</h3> -->\r\n" + 
				"  <div class=\"card-body\">\r\n" + 
				"    <div id=\"table\" class=\"table-editable\">\r\n" + 
				"      <span class=\"table-add float-right mb-3 mr-2\"><a href=\"#!\" class=\"text-success\"><i\r\n" + 
				"            class=\"fas fa-plus fa-2x\" aria-hidden=\"true\"></i></a></span>\r\n" + 
				"      <table class=\"table table-bordered table-responsive-md table-striped text-center\">\r\n" + 
				"        <thead>\r\n" + 
				"          <tr>\r\n" + 
				"            <th class=\"text-center\">Auction Name</th>\r\n" + 
				"            <th class=\"text-center\">Update</th>\r\n" + 
				"            <th class=\"text-center\">Delete</th>\r\n" + 
				"          </tr>\r\n" + 
				"        </thead>";
		for(Auction auc : au) {
			html+=" <tbody>\r\n";
					
			if(whoAmI.getUserId()==auc.getAuctionBy().getUserId() ) {
				html+="    <tr class=\\\"hide\\\"> <td class=\\\"pt-3-half\\\" contenteditable=\\\"true\\\">"+auc.getAuctionTitle()+" </td>\n ";
				html+="<td><span class=\"table-update\"><button type=\"button\"\r\n" +
						"onclick='updateAuction(\""+auc.getAuctionId()+"\")'  class=\"btn btn-danger btn-rounded btn-sm my-0\">Update</button></span>\r\n" + 
						"            </td>\r\n" + 
						"            <td><span class=\"table-delete\"><button type=\"button\"\r\n" + "onclick='deleteAuction(\""+auc.getAuctionId()+
						"                 \"\\\")' value='submit' class=\"btn btn-danger btn-rounded btn-sm my-0\">Delete</button></span>\r\n" + 
						"            </td>\r\n" + 
						"          </tr>\r\n";
				
			}
			
			   
		}
		html+="        </tbody>\r\n" + 
				"      </table>\r\n" + 
				"      </body>\r\n" + 
				"    </div>\r\n" + 
				"  </div>\r\n" + 
				"</div>";
	
		
		return html;
	}
	
	@RequestMapping("/ManageAuction/deleteAuction/{auctionId}")
	@ResponseBody
	public String deleteAuction(@PathVariable(value = "auctionId") Integer auctionId){
		String username=SecurityContextHolder.getContext().getAuthentication().getName();
		User whoAmI=userRepository.findByUsername(username);
		
		Auction au=auctionRepository.findById(auctionId).get();
		
		if(au.getAuctionBy().getUserId()==whoAmI.getUserId())
		{
			auctionRepository.delete(au);
			return "Auction Deleted";
		}
		
		return "/ManageAuction";
	}
}
